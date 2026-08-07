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
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.bridge.Bridge;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of the requests that are currently being processed by this PXF instance.
 * A request is registered when it starts being processed by
 * `BaseServiceImpl.processData()` and unregistered when it ends.
 */
@Component
@Slf4j
public class ActiveRequestRegistry {

    /** Sentinel meaning "all segments" for the segment-id filter. */
    public static final int ALL_SEGMENTS = -1;

    /** All in-flight requests on this instance. */
    private final Set<ActiveRequest> activeRequests = ConcurrentHashMap.newKeySet();

    /**
     * The request currently being processed by the calling worker thread, used
     * by the worker itself `attachBridge`, `isCurrentCancelled`) and to locate
     * the entry to remove on `unregister` without threading a handle through the
     * processing call chain. Set on `register` and cleared on `unregister`.
     */
    private final ThreadLocal<ActiveRequest> currentRequest = new ThreadLocal<>();

    private final String hostname;

    public ActiveRequestRegistry() {
        this.hostname = resolveHostname();
    }

    /**
     * Registers the request being processed by the calling thread as in-flight.
     * Must be paired with a call to `unregister()` in a finally block, on the
     * same thread.
     *
     * @param context the request context of the request being processed
     */
    public void register(RequestContext context) {
        ActiveRequestInfo info = new ActiveRequestInfo(context, System.currentTimeMillis(), hostname);
        ActiveRequest request = new ActiveRequest(info, Thread.currentThread());
        activeRequests.add(request);
        currentRequest.set(request);
    }

    /**
     * Removes the request registered by the calling thread from the registry and
     * marks it finished so that any concurrent `cancel()` / `interrupt` can no
     * longer act on the (soon to be recycled) worker thread. No-op if the calling
     * thread has no registered request.
     */
    public void unregister() {
        ActiveRequest request = currentRequest.get();
        if (request != null) {
            activeRequests.remove(request);
            request.markFinished();
        }
        currentRequest.remove();
    }

    /**
     * Records the bridge the calling worker thread is currently iterating over,
     * so that a concurrent `cancel` can end it. Pass `null` to clear
     * the reference once the bridge is closed. No-op if the caller is not a
     * registered worker thread.
     *
     * @param bridge the bridge in use, or `null` to detach
     */
    public void attachBridge(Bridge bridge) {
        ActiveRequest request = currentRequest.get();
        if (request != null) {
            request.setBridge(bridge);
        }
    }

    /**
     * @return whether the request being processed by the calling worker thread
     * has been asked to cancel. Polled by the read/write loops so they stop
     * between fragments/records.
     */
    public boolean isCurrentCancelled() {
        ActiveRequest request = currentRequest.get();
        return request != null && request.isCancelled();
    }

    /**
     * Returns a snapshot of the currently active requests, optionally filtered
     * by the originating segment id.
     *
     * @param segmentId the originating segment id to filter by, or
     *                  `ALL_SEGMENTS` to return activity for all segments
     * @return list of active request descriptors
     */
    public List<ActiveRequestInfo> snapshot(int segmentId) {
        List<ActiveRequestInfo> result = new ArrayList<>(activeRequests.size());
        for (ActiveRequest request : activeRequests) {
            if (matchesSegment(request, segmentId)) {
                result.add(request.info);
            }
        }
        return result;
    }

    /**
     * Gracefully cancels every active request of the given Cloudberry session on
     * this segment by ending its current bridge (see `ActiveRequest.cancelIfActive()`).
     *
     * @param segmentId the originating segment id, or `ALL_SEGMENTS`
     * @param sessionId the Cloudberry session id whose requests to cancel
     * @return the number of active requests that were signalled
     */
    public int cancel(int segmentId, int sessionId) {
        int count = 0;
        for (ActiveRequest request : activeRequests) {
            if (matchesSegment(request, segmentId) && request.info.getGpSessionId() == sessionId) {
                if (request.cancelIfActive()) {
                    count++;
                }
            }
        }
        log.info("pxf_cancel_backend: signalled {} request(s) for session {} on segment {}",
                count, sessionId, segmentId);
        return count;
    }

    /**
     * Interrupts the worker thread of every active request of the given
     * Cloudberry session on this segment (see `ActiveRequest.interruptIfActive()`).
     *
     * @param segmentId the originating segment id, or `ALL_SEGMENTS`
     * @param sessionId the Cloudberry session id whose requests to interrupt
     * @return the number of active requests that were interrupted
     */
    public int interrupt(int segmentId, int sessionId) {
        int count = 0;
        for (ActiveRequest request : activeRequests) {
            if (matchesSegment(request, segmentId) && request.info.getGpSessionId() == sessionId) {
                if (request.interruptIfActive()) {
                    count++;
                }
            }
        }
        log.info("pxf_interrupt_backend: interrupted {} request(s) for session {} on segment {}",
                count, sessionId, segmentId);
        return count;
    }

    private static boolean matchesSegment(ActiveRequest request, int segmentId) {
        return segmentId == ALL_SEGMENTS || request.info.getSegmentId() == segmentId;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Unable to resolve local hostname for pxf_stat_activity reporting", e);
            return "unknown";
        }
    }
}
