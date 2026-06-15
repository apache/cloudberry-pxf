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
 * TestContainers wrapper around MinIO for S3 / S3 Select automation tests.
 * The container joins a shared Docker network with alias minio, so PXF inside the
 * Cloudberry container can reach it at http://minio:9000.
 *
 * This class only manages the container lifecycle and exposes endpoint /
 * credential accessors. S3 API access (buckets, objects) lives in
 * {@link org.apache.cloudberry.pxf.automation.applications.S3Application}.
 */
public class MinIOContainer extends GenericContainer<MinIOContainer> {

    private static final String DEFAULT_IMAGE = "minio/minio:RELEASE.2024-11-07T00-52-20Z";
    private static final String NETWORK_ALIAS = "minio";

    public static final int API_PORT = 9000;
    public static final int CONSOLE_PORT = 9001;

    public static final String ACCESS_KEY = "admin";
    public static final String SECRET_KEY = "password";
    public static final String DEFAULT_BUCKET = "gpdb-ud-scratch";

    public MinIOContainer(Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE));

        withNetwork(network)
                .withNetworkAliases(NETWORK_ALIAS)
                .withExposedPorts(API_PORT, CONSOLE_PORT)
                .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                .withEnv("MINIO_API_SELECT_PARQUET", "on")
                .withCommand("server", "/data", "--console-address", ":" + CONSOLE_PORT)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(API_PORT));
    }

    /** S3 API endpoint reachable from the test JVM (mapped port). */
    public String getHostEndpoint() {
        return "http://localhost:" + getMappedPort(API_PORT);
    }

    /** S3 API endpoint for PXF and other containers on the same Docker network. */
    public String getInternalEndpoint() {
        return "http://" + NETWORK_ALIAS + ":" + API_PORT;
    }

    public String getAccessKey() {
        return ACCESS_KEY;
    }

    public String getSecretKey() {
        return SECRET_KEY;
    }
}
