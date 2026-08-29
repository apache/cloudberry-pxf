package org.apache.cloudberry.pxf.service.rest;

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

import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * REST endpoints that terminate in-flight PXF requests, backing the
 * `pxf_cancel_backend` and `pxf_interrupt_backend` SQL functions.
 */
@RestController
@RequestMapping("/pxf")
public class PxfBackendControlResource {

    private static final String SEGMENT_ID_HEADER = "X-GP-SEGMENT-ID";
    private static final String SESSION_ID_HEADER = "X-GP-SESSION-ID";

    private final ActiveRequestRegistry activeRequestRegistry;

    public PxfBackendControlResource(ActiveRequestRegistry activeRequestRegistry) {
        this.activeRequestRegistry = activeRequestRegistry;
    }

    /**
     * Gracefully cancels the active requests of the given session on this segment
     * by ending their current bridge.
     *
     * @param sessionId the Cloudberry session id whose requests to cancel
     * @param segmentId the originating segment id; when absent, all segments
     * @return a JSON object `{"cancelled":N}` with the number signalled
     */
    @GetMapping(value = "/cancel_backend", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Integer>> cancelBackend(
            @RequestHeader(SESSION_ID_HEADER) int sessionId,
            @RequestHeader(value = SEGMENT_ID_HEADER, required = false) Integer segmentId) {
        int filter = (segmentId == null) ? ActiveRequestRegistry.ALL_SEGMENTS : segmentId;
        int cancelled = activeRequestRegistry.cancel(filter, sessionId);
        return ResponseEntity.ok(Collections.singletonMap("cancelled", cancelled));
    }

    /**
     * Interrupts the worker thread(s) of the active requests of the given session
     * on this segment.
     *
     * @param sessionId the Cloudberry session id whose requests to interrupt
     * @param segmentId the originating segment id; when absent, all segments
     * @return a JSON object `{"interrupted":N}` with the number interrupted
     */
    @GetMapping(value = "/interrupt_backend", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Integer>> interruptBackend(
            @RequestHeader(SESSION_ID_HEADER) int sessionId,
            @RequestHeader(value = SEGMENT_ID_HEADER, required = false) Integer segmentId) {
        int filter = (segmentId == null) ? ActiveRequestRegistry.ALL_SEGMENTS : segmentId;
        int interrupted = activeRequestRegistry.interrupt(filter, sessionId);
        return ResponseEntity.ok(Collections.singletonMap("interrupted", interrupted));
    }
}
