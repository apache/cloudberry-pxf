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

import org.apache.cloudberry.pxf.service.HttpHeaderDecoder;
import org.apache.cloudberry.pxf.service.activity.ActiveRequestRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PxfBackendControlResource.class)
public class PxfBackendControlResourceIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ActiveRequestRegistry mockRegistry;

    // required by web-layer beans pulled into the @WebMvcTest slice
    @MockBean
    private HttpHeaderDecoder mockHttpHeaderDecoder;

    @Test
    public void cancelBackendFiltersBySegmentAndSessionHeaders() throws Exception {
        when(mockRegistry.cancel(eq(3), eq(42))).thenReturn(2);

        mvc.perform(get("/pxf/cancel_backend")
                        .header("X-GP-SEGMENT-ID", "3")
                        .header("X-GP-SESSION-ID", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2));

        verify(mockRegistry).cancel(3, 42);
    }

    @Test
    public void cancelBackendWithoutSegmentHeaderTargetsAllSegments() throws Exception {
        when(mockRegistry.cancel(eq(ActiveRequestRegistry.ALL_SEGMENTS), eq(42))).thenReturn(1);

        mvc.perform(get("/pxf/cancel_backend").header("X-GP-SESSION-ID", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(1));

        verify(mockRegistry).cancel(ActiveRequestRegistry.ALL_SEGMENTS, 42);
    }

    @Test
    public void interruptBackendFiltersBySegmentAndSessionHeaders() throws Exception {
        when(mockRegistry.interrupt(eq(3), eq(42))).thenReturn(1);

        mvc.perform(get("/pxf/interrupt_backend")
                        .header("X-GP-SEGMENT-ID", "3")
                        .header("X-GP-SESSION-ID", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interrupted").value(1));

        verify(mockRegistry).interrupt(3, 42);
    }

    @Test
    public void missingSessionHeaderIsBadRequest() throws Exception {
        mvc.perform(get("/pxf/cancel_backend").header("X-GP-SEGMENT-ID", "3"))
                .andExpect(status().isBadRequest());
    }
}
