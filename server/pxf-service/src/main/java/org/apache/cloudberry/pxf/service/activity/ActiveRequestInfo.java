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

import lombok.Getter;
import org.apache.cloudberry.pxf.api.model.RequestContext;

/**
 * Immutable snapshot of an in-flight PXF request, captured at the moment the
 * request started being processed. Instances are exposed via the
 * `/pxf/stat_activity` endpoint.
 * <p>
 * The getters are serialized to JSON by Jackson.
 */
@Getter
public class ActiveRequestInfo {

    /** The Cloudberry segment id that originated the request. */
    private final int segmentId;

    /** The Cloudberry session id (aka ssid). */
    private final int gpSessionId;

    /** The Cloudberry command count within the session (aka ccnt). */
    private final int gpCommandCount;

    /** The Cloudberry transaction id (XID) of the originating query. */
    private final String transactionId;

    /** The kind of operation: READ_BRIDGE or WRITE_BRIDGE. */
    private final String requestType;

    /** The end-user identity that issued the request. */
    private final String user;

    /** The name of the PXF server configuration used by the request. */
    private final String serverName;

    /** The profile associated with the request (e.g. hdfs:text). */
    private final String profile;

    /** The originating Cloudberry schema name. */
    private final String schemaName;

    /** The originating Cloudberry table name. */
    private final String tableName;

    /** The data source (file path or external resource identifier). */
    private final String dataSource;

    /** Epoch milliseconds when the request started being processed. */
    private final long startTimeMs;

    /** The hostname of the PXF instance that is serving the request. */
    private final String host;

    /**
     * Captures a snapshot of the relevant fields from the request context.
     *
     * @param context     the request context of the in-flight request
     * @param startTimeMs epoch milliseconds when processing started
     * @param host        hostname of the PXF instance serving the request
     */
    public ActiveRequestInfo(RequestContext context, long startTimeMs, String host) {
        this.segmentId = context.getSegmentId();
        this.gpSessionId = context.getGpSessionId();
        this.gpCommandCount = context.getGpCommandCount();
        this.transactionId = context.getTransactionId();
        this.requestType = context.getRequestType() == null ? null : context.getRequestType().name();
        this.user = context.getUser();
        this.serverName = context.getServerName();
        this.profile = context.getProfile();
        this.schemaName = context.getSchemaName();
        this.tableName = context.getTableName();
        this.dataSource = context.getDataSource();
        this.startTimeMs = startTimeMs;
        this.host = host;
    }
}
