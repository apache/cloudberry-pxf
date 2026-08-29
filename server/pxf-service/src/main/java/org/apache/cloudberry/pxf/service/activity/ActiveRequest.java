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

import lombok.extern.slf4j.Slf4j;
import org.apache.cloudberry.pxf.service.bridge.Bridge;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A single in-flight PXF request tracked by {@link ActiveRequestRegistry}.
 * <p>
 * In addition to the immutable {@link ActiveRequestInfo} snapshot (which defines
 * the `pxf_stat_activity` JSON wire contract) it holds the two live
 * handles used to terminate the request from another thread:
 * - the worker `thread` that is processing it (for `pxf_interrupt_backend`)
 * - the current `bridge` (for `pxf_cancel_backend`)
 */
@Slf4j
class ActiveRequest {

    /*
     * Concurrency: PXF processes a request synchronously on a servlet
     * thread that is returned to a pool afterwards, so a naive
     * `thread.interrupt()` could land on an unrelated request the thread has
     * since picked up. To prevent that, every mutation that races with request
     * completion is serialized on `lock` and gated by the `finished`
     * flag:
     * <ul>
     *   <li>the worker calls `markFinished()` in its `finally` block
     *       before it can return to the pool;</li>
     *   <li>`cancelIfActive()` / `interruptIfActive()` only act while
     *       `finished == false`, which — because both sides take the same
     *       lock — guarantees the worker is still executing this request.</li>
     * </ul>
     */

    /** Immutable snapshot exposed via {@code pxf_stat_activity}. */
    final ActiveRequestInfo info;

    /** The servlet worker thread that is processing this request. */
    private final Thread thread;

    /** Serializes cancellation/interruption against request completion. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Set once the request has completed; guarded by {@link #lock}. */
    private boolean finished;

    /** Set when the request has been asked to stop; read by the worker loop. */
    private volatile boolean cancelled;

    /** The bridge currently in use by the worker; guarded by {@link #lock}. */
    private Bridge bridge;

    ActiveRequest(ActiveRequestInfo info, Thread thread) {
        this.info = info;
        this.thread = thread;
    }

    /**
     * @return whether this request has been asked to stop; polled by the worker
     * loop so it can break out between fragments/records without waiting for a
     * blocking read to be unblocked by {@link #cancelIfActive()}.
     */
    boolean isCancelled() {
        return cancelled;
    }

    /**
     * Records (or clears, with {@code null}) the bridge the worker is currently
     * iterating over, so that {@link #cancelIfActive()} can end it.
     */
    void setBridge(Bridge bridge) {
        lock.lock();
        try {
            this.bridge = bridge;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks the request as completed. After this returns, {@link #cancelIfActive()}
     * and {@link #interruptIfActive()} become no-ops, so the worker thread can be
     * safely returned to the pool without risking a stray interrupt.
     */
    void markFinished() {
        lock.lock();
        try {
            finished = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Requests cancellation of this request if it is still active: raises the
     * {@link #cancelled} flag and ends the current bridge to unblock a pending
     * read. Safe to call concurrently with the worker's own bridge lifecycle;
     * {@code endIteration()} must therefore be idempotent enough to tolerate a
     * double close (its error is logged and swallowed).
     *
     * @return {@code true} if the request was still active and was signalled
     */
    boolean cancelIfActive() {
        lock.lock();
        try {
            if (finished) {
                return false;
            }
            cancelled = true;
            if (bridge != null) {
                try {
                    bridge.endIteration();
                } catch (Exception e) {
                    log.warn("Ignoring error while cancelling bridge for {}", describe(), e);
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Interrupts the worker thread if the request is still active. Holding the
     * lock while checking {@link #finished} guarantees the thread has not yet
     * returned to the pool, so the interrupt cannot leak onto a later request.
     *
     * @return {@code true} if the request was still active and was interrupted
     */
    boolean interruptIfActive() {
        lock.lock();
        try {
            if (finished) {
                return false;
            }
            thread.interrupt();
            return true;
        } finally {
            lock.unlock();
        }
    }

    private String describe() {
        return String.format("session %d, segment %d, xid %s",
                info.getGpSessionId(), info.getSegmentId(), info.getTransactionId());
    }
}
