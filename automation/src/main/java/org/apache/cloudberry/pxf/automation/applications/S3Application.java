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
                "S3SITE"
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

    // Writes s3-site.xml with invalid credentials for negative credential-resolution tests.
    public void configureInvalidS3Server(MinIOContainer minio, String serverName) throws IOException, InterruptedException {
        String endpoint = minio.getInternalEndpoint();

        System.out.println("[S3Application] Configuring invalid PXF server '" + serverName + "'...");

        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "TEMPLATES_DIR=${PXF_HOME}/templates",
                "PXF_BASE_SERVERS=${PXF_BASE}/servers",
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
                "        <value>invalid-access-key</value>",
                "    </property>",
                "    <property>",
                "        <name>fs.s3a.secret.key</name>",
                "        <value>invalid-secret-key</value>",
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
                "S3SITE"
        );

        ExecResult result = container.execInContainer("bash", "-l", "-c", script);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    "Invalid S3 server configuration failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }

        new PXFApplication(container).restartPxf();
        System.out.println("[S3Application] Invalid PXF server '" + serverName + "' configured");
    }

    // Writes s3-site.xml with endpoint only (no credentials) for credential-via-URL tests.
    public void configureServerEndpointOnly(MinIOContainer minio, String serverName)
            throws IOException, InterruptedException {
        String endpoint = minio.getInternalEndpoint();

        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "TEMPLATES_DIR=${PXF_HOME}/templates",
                "PXF_BASE_SERVERS=${PXF_BASE}/servers",
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
                "S3SITE"
        );

        ExecResult result = container.execInContainer("bash", "-l", "-c", script);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    "Endpoint-only S3 server configuration failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }

        new PXFApplication(container).restartPxf();
        System.out.println("[S3Application] Endpoint-only PXF server '" + serverName + "' configured");
    }

    public void clearGpadminAwsCredentials() throws IOException, InterruptedException {
        runContainerScript("rm -f /home/gpadmin/.aws/credentials", "Cleared gpadmin AWS credentials file");
    }

    public void removeServerDirectory(String serverName) throws IOException, InterruptedException {
        runContainerScript(
                "rm -rf \"${PXF_BASE}/servers/" + serverName + "\"",
                "Removed PXF server directory '" + serverName + "'");
    }

    // Removes all Hadoop site files from the default PXF server (no HDFS cluster configured).
    public void stripDefaultServerHdfsConfig() throws IOException, InterruptedException {
        runDefaultServerScript(
                "rm -f \"${SERVER_DIR}\"/hdfs-site.xml \"${SERVER_DIR}\"/mapred-site.xml"
                        + " \"${SERVER_DIR}\"/yarn-site.xml \"${SERVER_DIR}\"/core-site.xml"
                        + " \"${SERVER_DIR}\"/hbase-site.xml \"${SERVER_DIR}\"/hive-site.xml"
                        + " \"${SERVER_DIR}\"/s3-site.xml",
                "Stripped Hadoop/S3 site config from default PXF server");
        clearGpadminAwsCredentials();
    }

    // Restores Hadoop site files on the default PXF server from PXF templates.
    public void restoreDefaultServerHdfsConfig() throws IOException, InterruptedException {
        runDefaultServerScript(
                "cp \"${TEMPLATES_DIR}\"/hdfs-site.xml \"${SERVER_DIR}/\""
                        + " && cp \"${TEMPLATES_DIR}\"/mapred-site.xml \"${SERVER_DIR}/\""
                        + " && cp \"${TEMPLATES_DIR}\"/yarn-site.xml \"${SERVER_DIR}/\""
                        + " && cp \"${TEMPLATES_DIR}\"/core-site.xml \"${SERVER_DIR}/\""
                        + " && cp \"${TEMPLATES_DIR}\"/hbase-site.xml \"${SERVER_DIR}/\""
                        + " && cp \"${TEMPLATES_DIR}\"/hive-site.xml \"${SERVER_DIR}/\"",
                "Restored Hadoop config on default PXF server");
    }

    private void runDefaultServerScript(String serverDirAction, String logMessage)
            throws IOException, InterruptedException {
        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "TEMPLATES_DIR=${PXF_HOME}/templates",
                "SERVER_DIR=${PXF_BASE}/servers/default",
                serverDirAction
        );
        runContainerScript(script, logMessage);
    }

    private void runContainerScript(String body, String logMessage) throws IOException, InterruptedException {
        ExecResult result = container.execInContainer("bash", "-l", "-c", body);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    logMessage + " failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }

        if (body.contains("SERVER_DIR") || body.contains("servers/")) {
            new PXFApplication(container).restartPxf();
        }
        System.out.println("[S3Application] " + logMessage);
    }
}
