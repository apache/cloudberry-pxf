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
 * Testcontainers wrapper around Microsoft SQL Server.
 *
 * The container joins a shared Docker network with a version-derived alias so that PXF
 * inside the Cloudberry container can reach it at `mssql-<tag>:1433`.
 */
public class MSSQLServerContainer
        extends org.testcontainers.containers.MSSQLServerContainer<MSSQLServerContainer> {

    private static final String DEFAULT_IMAGE = "mcr.microsoft.com/mssql/server";
    private static final String NETWORK_ALIAS_PREFIX = "mssql-";

    public static final String MSSQL_USER = "SA";
    public static final String MSSQL_PASSWORD = "Pxf-Test1!";

    private final String networkAlias;

    public MSSQLServerContainer(String tag, Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE + ":" + tag)
                .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"));

        this.networkAlias = NETWORK_ALIAS_PREFIX + tag.replaceAll("[-.]", "");

        acceptLicense();
        withNetwork(network)
                .withNetworkAliases(this.networkAlias)
                .withPassword(MSSQL_PASSWORD);
    }

    /**
     * JDBC URL for PXF / other containers on the same Docker network.
     * Uses `trustServerCertificate=true` because the container uses a self-signed certificate.
     */
    public String getInternalJdbcUrl() {
        return "jdbc:sqlserver://" + networkAlias + ":" + MS_SQL_SERVER_PORT + ";"
                + "databaseName=master;"
                + "encrypt=true;"
                + "trustServerCertificate=true";
    }

    /**
     * JDBC URL reachable from the host (mapped port), used to seed data from the test JVM.
     * Overrides parent to append TLS flags required for the container's self-signed cert.
     */
    @Override
    public String getJdbcUrl() {
        return "jdbc:sqlserver://localhost:" + getMappedPort(MS_SQL_SERVER_PORT) + ";"
                + "databaseName=master;"
                + "encrypt=true;"
                + "trustServerCertificate=true";
    }
}
