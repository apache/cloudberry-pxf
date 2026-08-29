package org.apache.cloudberry.pxf.service.activity;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.cloudberry.pxf.api.io.Writable;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.bridge.Bridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActiveRequestRegistryTest {

    private ActiveRequestRegistry registry;

    @BeforeEach
    public void setup() {
        registry = new ActiveRequestRegistry();
    }

    private RequestContext context(int segmentId, String xid) {
        return context(segmentId, xid, 0);
    }

    private RequestContext context(int segmentId, String xid, int sessionId) {
        RequestContext context = new RequestContext();
        context.setRequestType(RequestContext.RequestType.READ_BRIDGE);
        context.setSegmentId(segmentId);
        context.setTransactionId(xid);
        context.setGpSessionId(sessionId);
        return context;
    }

    @Test
    public void emptyRegistryReturnsNoActivity() {
        assertTrue(registry.snapshot(ActiveRequestRegistry.ALL_SEGMENTS).isEmpty());
        assertTrue(registry.snapshot(0).isEmpty());
    }

    @Test
    public void registeredRequestIsVisibleThenRemovedOnUnregister() {
        registry.register(context(3, "100"));

        List<ActiveRequestInfo> all = registry.snapshot(ActiveRequestRegistry.ALL_SEGMENTS);
        assertEquals(1, all.size());
        assertEquals(3, all.get(0).getSegmentId());
        assertEquals("100", all.get(0).getTransactionId());
        assertEquals("READ_BRIDGE", all.get(0).getRequestType());

        registry.unregister();
        assertTrue(registry.snapshot(ActiveRequestRegistry.ALL_SEGMENTS).isEmpty());
    }

    @Test
    public void snapshotFiltersBySegmentId() {
        registry.register(context(0, "100"));
        registry.register(context(1, "101"));
        registry.register(context(1, "102"));

        assertEquals(3, registry.snapshot(ActiveRequestRegistry.ALL_SEGMENTS).size());
        assertEquals(1, registry.snapshot(0).size());
        assertEquals(2, registry.snapshot(1).size());
        assertTrue(registry.snapshot(2).isEmpty());
    }

    @Test
    public void concurrentRequestsTrackedIndependently() throws Exception {
        // Each request registers/unregisters on its own thread, as the real
        // servlet workers do; entries are tracked by ActiveRequest identity.
        Worker w1 = new Worker(context(0, "100"), null);
        Worker w2 = new Worker(context(0, "101"), null);
        w1.start();
        w2.start();

        assertEquals(2, registry.snapshot(0).size());
        w1.finishAndJoin();
        assertEquals(1, registry.snapshot(0).size());
        w2.finishAndJoin();
        assertTrue(registry.snapshot(0).isEmpty());
    }

    @Test
    public void cancelEndsBridgeAndRaisesCancelledFlagForMatchingSession() throws Exception {
        CountingBridge bridge = new CountingBridge();
        Worker worker = new Worker(context(0, "100", 42), bridge);
        worker.start();

        assertEquals(1, registry.cancel(0, 42));
        assertEquals(1, bridge.endIterationCount.get());

        worker.finishAndJoin();
        assertTrue(worker.cancelledSeen, "worker should observe the cancelled flag via isCurrentCancelled()");
        assertFalse(worker.interrupted, "cancel must not interrupt the worker thread");
    }

    @Test
    public void cancelIgnoresOtherSessionsAndSegments() throws Exception {
        CountingBridge bridge = new CountingBridge();
        Worker worker = new Worker(context(0, "100", 42), bridge);
        worker.start();

        assertEquals(0, registry.cancel(0, 99), "different session");
        assertEquals(0, registry.cancel(1, 42), "different segment");
        assertEquals(0, bridge.endIterationCount.get());

        worker.finishAndJoin();
    }

    @Test
    public void interruptWakesMatchingWorkerThread() throws Exception {
        Worker worker = new Worker(context(0, "100", 42), null);
        worker.start();

        assertEquals(1, registry.interrupt(0, 42));

        // interrupting unblocks the worker's interruptible wait on its own
        worker.join(5000);
        assertFalse(worker.isAlive(), "interrupted worker should have finished");
        assertTrue(worker.interrupted, "worker thread should have been interrupted");
    }

    @Test
    public void interruptIgnoresOtherSessions() throws Exception {
        Worker worker = new Worker(context(0, "100", 42), null);
        worker.start();

        assertEquals(0, registry.interrupt(0, 99));

        worker.finishAndJoin();
        assertFalse(worker.interrupted);
    }

    @Test
    public void cancelAndInterruptAreNoOpsAfterUnregister() {
        // register and unregister synchronously on this thread so the entry is
        // marked finished; both operations must then refuse to act
        registry.register(context(0, "100", 42));
        registry.unregister();

        assertEquals(0, registry.cancel(0, 42));
        assertEquals(0, registry.interrupt(0, 42));
        assertFalse(Thread.currentThread().isInterrupted(), "the test thread must not be interrupted");
    }

    /**
     * A worker that registers itself (capturing its own thread, as the real
     * servlet worker does), optionally attaches a bridge, then blocks on an
     * interruptible latch until released or interrupted, and finally unregisters.
     */
    private final class Worker extends Thread {
        private final RequestContext context;
        private final Bridge bridge;
        private final CountDownLatch registered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        volatile boolean interrupted;
        volatile boolean cancelledSeen;

        Worker(RequestContext context, Bridge bridge) {
            this.context = context;
            this.bridge = bridge;
        }

        @Override
        public void run() {
            registry.register(context);
            if (bridge != null) {
                registry.attachBridge(bridge);
            }
            registered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                cancelledSeen = registry.isCurrentCancelled();
                registry.unregister();
            }
        }

        @Override
        public synchronized void start() {
            super.start();
            try {
                assertTrue(registered.await(5, TimeUnit.SECONDS), "worker failed to register");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        void finishAndJoin() throws InterruptedException {
            release.countDown();
            join(5000);
            assertFalse(isAlive(), "worker should have finished");
        }
    }

    /** Minimal bridge that only counts endIteration() invocations. */
    private static final class CountingBridge implements Bridge {
        final AtomicInteger endIterationCount = new AtomicInteger();
        final AtomicBoolean began = new AtomicBoolean();

        @Override
        public boolean beginIteration() {
            began.set(true);
            return true;
        }

        @Override
        public Writable getNext() {
            return null;
        }

        @Override
        public boolean setNext(DataInputStream inputStream) {
            return false;
        }

        @Override
        public void endIteration() {
            endIterationCount.incrementAndGet();
        }
    }
}
