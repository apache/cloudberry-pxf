package org.apache.cloudberry.pxf.automation.testcontainers;

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

import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers wrapper around Oracle Database Free.
 *
 * The container joins a shared Docker network with alias `oracle`,
 * so PXF inside the Cloudberry container can reach it at `oracle:1521`
 *
 */
public class OracleContainer extends org.testcontainers.oracle.OracleContainer {

    private static final String DEFAULT_IMAGE = "gvenzl/oracle-free:23-slim";
    private static final String NETWORK_ALIAS = "oracle";

    public static final int ORACLE_PORT = 1521;
    public static final String SERVICE_NAME = "FREE";

    public static final String ORACLE_USER = "system";
    public static final String ORACLE_PASSWORD = "pxf-test";

    public OracleContainer(Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE));

        withPassword(ORACLE_PASSWORD);
        withNetwork(network);
        withNetworkAliases(NETWORK_ALIAS);
    }

    /** JDBC URL reachable from the host (mapped `ORACLE_PORT`) */
    @Override
    public String getJdbcUrl() {
        return "jdbc:oracle:thin:@localhost:" + getMappedPort(ORACLE_PORT) + "/" + SERVICE_NAME;
    }

    /** JDBC URL for PXF / other containers on the same Docker network */
    public String getInternalJdbcUrl() {
        return "jdbc:oracle:thin:@" + NETWORK_ALIAS + ":" + ORACLE_PORT + "/" + SERVICE_NAME;
    }
}
