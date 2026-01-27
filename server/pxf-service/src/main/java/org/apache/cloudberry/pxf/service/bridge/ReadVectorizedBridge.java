package org.apache.cloudberry.pxf.service.bridge;

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

import org.apache.cloudberry.pxf.api.OneField;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.ReadVectorizedResolver;
import org.apache.cloudberry.pxf.api.io.Writable;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.service.utilities.BasePluginFactory;
import org.apache.cloudberry.pxf.service.utilities.GSSFailureHandler;

import java.util.Deque;
import java.util.List;

public class ReadVectorizedBridge extends ReadBridge {

    public ReadVectorizedBridge(BasePluginFactory pluginFactory, RequestContext context, GSSFailureHandler failureHandler) {
        super(pluginFactory, context, failureHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Deque<Writable> makeOutput(OneRow oneRow) throws Exception {
        List<List<OneField>> resolvedBatch = ((ReadVectorizedResolver) resolver).
                getFieldsForBatch(oneRow);
        return outputBuilder.makeVectorizedOutput(resolvedBatch);
    }
}
