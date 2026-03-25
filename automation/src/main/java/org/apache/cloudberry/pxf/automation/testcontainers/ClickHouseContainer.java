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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers wrapper around ClickHouse server.
 *
 * The container joins a shared Docker network with alias `clickhouse`,
 * so PXF inside the Cloudberry container can reach it at `clickhouse:8123` (HTTP).
 *
 */
public class ClickHouseContainer extends GenericContainer<ClickHouseContainer> {

    public static final int HTTP_PORT = 8123;

    private static final String DEFAULT_IMAGE = "clickhouse/clickhouse-server";
    private static final String NETWORK_ALIAS_PREFIX = "clickhouse-";

    /**
     * Credentials for the test container. ClickHouse 24+ restricts network access for `default`
     * until a password is set; `CLICKHOUSE_PASSWORD` configures the server and JDBC must match.
     */
    public static final String CLICKHOUSE_USER = "default";
    public static final String CLICKHOUSE_PASSWORD = "pxf-test";

    private final String networkAlias;

    public ClickHouseContainer(String tag, Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE + ":" + tag));

        // generate unique DNS name for this Clickhouse container:
        this.networkAlias = NETWORK_ALIAS_PREFIX + tag.replaceAll("[-.]", "");

        super.withNetwork(network)
            .withNetworkAliases(this.networkAlias)
            .withExposedPorts(HTTP_PORT)
            .withEnv("CLICKHOUSE_USER", CLICKHOUSE_USER)
            .withEnv("CLICKHOUSE_PASSWORD", CLICKHOUSE_PASSWORD)
            .waitingFor(Wait.forHttp("/ping").forPort(HTTP_PORT));
    }

    /** Embedded DNS name of this container on the Testcontainers network (for JDBC from other containers). */
    public String getNetworkAlias() {
        return networkAlias;
    }

    /** JDBC URL over HTTP, reachable from the host (mapped `HTTP_PORT`). */
    public String getJdbcUrl() {
        return "jdbc:clickhouse://localhost:" + getMappedPort(HTTP_PORT) + "/default";
    }

    /** JDBC URL over HTTP for PXF / other containers on the same Docker network. */
    public String getInternalJdbcUrl() {
        return "jdbc:clickhouse://" + networkAlias + ":" + HTTP_PORT + "/default";
    }

    public int getHttpMappedPort() {
        return getMappedPort(HTTP_PORT);
    }

}
