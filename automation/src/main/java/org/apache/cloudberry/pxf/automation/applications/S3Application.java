package org.apache.cloudberry.pxf.automation.applications;

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

import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;
import org.apache.cloudberry.pxf.automation.testcontainers.PXFCloudberryContainer;
import org.testcontainers.containers.Container.ExecResult;

import java.io.IOException;

/**
 * Manages PXF S3 server configuration inside the Cloudberry test container.
 */
public class S3Application {

    private static final String SCRIPTS_PREFIX =
            "/home/gpadmin/workspace/cloudberry-pxf/automation/src/main/resources/testcontainers/pxf-cbdb/script";

    private final PXFCloudberryContainer container;

    public S3Application(PXFCloudberryContainer container) {
        this.container = container;
    }

    // Writes s3-site.xml and mapred-site.xml for the named PXF server and restarts PXF.
    public void configureS3Server(MinIOContainer minio, String serverName) throws IOException, InterruptedException {
        String endpoint = minio.getInternalEndpoint();
        String accessKey = minio.getAccessKey();
        String secretKey = minio.getSecretKey();

        System.out.println("[S3Application] Configuring PXF server '" + serverName + "' (endpoint=" + endpoint + ")...");

        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "PXF_BASE_SERVERS=${PXF_BASE}/servers",
                "TEMPLATES_DIR=${PXF_HOME}/templates",
                "SERVER_DIR=${PXF_BASE_SERVERS}/" + serverName,
                "mkdir -p \"${SERVER_DIR}\"",
                "cp \"${TEMPLATES_DIR}/mapred-site.xml\" \"${SERVER_DIR}/\"",
                "cat > \"${SERVER_DIR}/s3-site.xml\" <<'S3SITE'",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<configuration>",
                "    <property>",
                "        <name>fs.s3a.endpoint</name>",
                "        <value>" + endpoint + "</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.access.key</name>",
                "        <value>" + accessKey + "</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.secret.key</name>",
                "        <value>" + secretKey + "</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.path.style.access</name>",
                "        <value>true</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.connection.ssl.enabled</name>",
                "        <value>false</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.impl</name>",
                "        <value>org.apache.hadoop.fs.s3a.S3AFileSystem</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.aws.credentials.provider</name>",
                "        <value>org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider</value>",
                "    </property>",
                "</configuration>",
                "S3SITE",
                "mkdir -p /home/gpadmin/.aws",
                "cat > /home/gpadmin/.aws/credentials <<'AWSCREDS'",
                "[default]",
                "aws_access_key_id = " + accessKey,
                "aws_secret_access_key = " + secretKey,
                "AWSCREDS"
        );

        ExecResult result = container.execInContainer("bash", "-l", "-c", script);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    "S3 server configuration failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }

        new PXFApplication(container).restartPxf();
        System.out.println("[S3Application] PXF server '" + serverName + "' configured and PXF restarted");
    }
}
