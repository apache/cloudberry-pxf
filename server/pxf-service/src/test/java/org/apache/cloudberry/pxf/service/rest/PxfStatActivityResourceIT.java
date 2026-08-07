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

import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.HttpHeaderDecoder;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestInfo;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PxfStatActivityResource.class)
public class PxfStatActivityResourceIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ActiveRequestRegistry mockRegistry;

    @MockBean
    private HttpHeaderDecoder mockHttpHeaderDecoder;

    private static ActiveRequestInfo sampleInfo(int segmentId, String xid) {
        RequestContext context = new RequestContext();
        context.setRequestType(RequestContext.RequestType.READ_BRIDGE);
        context.setSegmentId(segmentId);
        context.setTransactionId(xid);
        context.setGpSessionId(42);
        context.setGpCommandCount(7);
        context.setProfile("hdfs:text");
        return new ActiveRequestInfo(context, 1718700000000L, "sdw3");
    }

    @Test
    public void emptyRegistryReturnsEmptyActivities() throws Exception {
        when(mockRegistry.snapshot(ActiveRequestRegistry.ALL_SEGMENTS))
                .thenReturn(Collections.emptyList());

        mvc.perform(get("/pxf/stat_activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isArray())
                .andExpect(jsonPath("$.activities").isEmpty());
    }

    @Test
    public void filtersBySegmentIdHeaderAndSerializesFields() throws Exception {
        when(mockRegistry.snapshot(eq(3)))
                .thenReturn(Collections.singletonList(sampleInfo(3, "1234")));

        mvc.perform(get("/pxf/stat_activity").header("X-GP-SEGMENT-ID", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].segmentId").value(3))
                .andExpect(jsonPath("$.activities[0].transactionId").value("1234"))
                .andExpect(jsonPath("$.activities[0].requestType").value("READ_BRIDGE"))
                .andExpect(jsonPath("$.activities[0].gpSessionId").value(42))
                .andExpect(jsonPath("$.activities[0].profile").value("hdfs:text"))
                .andExpect(jsonPath("$.activities[0].host").value("sdw3"))
                .andExpect(jsonPath("$.activities[0].startTimeMs").value(1718700000000L));
    }
}
