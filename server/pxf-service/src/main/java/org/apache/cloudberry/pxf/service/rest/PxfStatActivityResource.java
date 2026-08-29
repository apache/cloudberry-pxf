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

import org.apache.cloudberry.pxf.service.activity.ActiveRequestInfo;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint that exposes the requests currently being processed by this PXF
 * instance. It backs the `pxf_stat_activity` SQL view.
 */
@RestController
@RequestMapping("/pxf")
public class PxfStatActivityResource {

    private static final String SEGMENT_ID_HEADER = "X-GP-SEGMENT-ID";

    private final ActiveRequestRegistry activeRequestRegistry;

    public PxfStatActivityResource(ActiveRequestRegistry activeRequestRegistry) {
        this.activeRequestRegistry = activeRequestRegistry;
    }

    /**
     * Returns the active requests known to this PXF instance, optionally
     * filtered by the originating segment id supplied in the request header.
     *
     * @param segmentId the originating segment id; when absent, activity for all
     *                  segments is returned
     * @return a JSON object `{"activities":[...]}`
     */
    @GetMapping(value = "/stat_activity", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<ActiveRequestInfo>>> statActivity(
            @RequestHeader(value = SEGMENT_ID_HEADER, required = false) Integer segmentId) {
        int filter = (segmentId == null) ? ActiveRequestRegistry.ALL_SEGMENTS : segmentId;
        List<ActiveRequestInfo> activities = activeRequestRegistry.snapshot(filter);
        return ResponseEntity.ok(Collections.singletonMap("activities", activities));
    }
}
