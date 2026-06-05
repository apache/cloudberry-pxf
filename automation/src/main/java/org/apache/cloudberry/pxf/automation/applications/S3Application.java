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
 * Runtime PXF server-config mutations needed by CloudAccess automation tests.
 *
 * The stable servers ({@code s3}, {@code s3-invalid}) are pre-baked into the
 * container image by entrypoint.sh. This class only handles things that must
 * change between tests: creating/removing the {@code s3-non-existent} server
 * with endpoint-only config, stripping or restoring the default server's
 * Hadoop XMLs, and clearing the gpadmin AWS credentials file.
 */
public class S3Application {

    private static final String SCRIPTS_PREFIX =
            "/home/gpadmin/workspace/cloudberry-pxf/automation/src/main/resources/testcontainers/pxf-cbdb/script";

    private final PXFCloudberryContainer container;

    public S3Application(PXFCloudberryContainer container) {
        this.container = container;
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
