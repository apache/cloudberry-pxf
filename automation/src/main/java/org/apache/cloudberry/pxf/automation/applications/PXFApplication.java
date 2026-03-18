package org.apache.cloudberry.pxf.automation.applications;

import org.apache.cloudberry.pxf.automation.testcontainers.PXFCloudberryContainer;
import org.testcontainers.containers.Container.ExecResult;

import java.io.IOException;

/**
 * Manages PXF server configuration inside the container.
 * Writes config files (jdbc-site.xml, s3-site.xml, etc.) and restarts the PXF process.
 */
public class PXFApplication {

    private static final String SCRIPTS_PREFIX =
            "/home/gpadmin/workspace/cloudberry-pxf/automation/src/main/resources/testcontainers/pxf-cbdb/script";

    private final PXFCloudberryContainer container;

    public PXFApplication(PXFCloudberryContainer container) {
        this.container = container;
    }

    public void configureJdbcServers() throws IOException, InterruptedException {
        System.out.println("[PXFApplication] Configuring JDBC servers (database, db-session-params, db-hive)...");

        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "PXF_BASE_SERVERS=${PXF_BASE}/servers",
                "TEMPLATES_DIR=${PXF_HOME}/templates",

                "mkdir -p ${PXF_BASE_SERVERS}/database",
                "cp ${TEMPLATES_DIR}/jdbc-site.xml ${PXF_BASE_SERVERS}/database/",
                "sed -i 's|YOUR_DATABASE_JDBC_DRIVER_CLASS_NAME|org.postgresql.Driver|' ${PXF_BASE_SERVERS}/database/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_URL|jdbc:postgresql://localhost:7000/pxfautomation|' ${PXF_BASE_SERVERS}/database/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_USER||' ${PXF_BASE_SERVERS}/database/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_PASSWORD||' ${PXF_BASE_SERVERS}/database/jdbc-site.xml",
                "cp ${PXF_BASE_SERVERS}/database/jdbc-site.xml ${PXF_BASE_SERVERS}/database/testuser-user.xml",
                "sed -i 's|pxfautomation|template1|' ${PXF_BASE_SERVERS}/database/testuser-user.xml",
                "cp /home/gpadmin/workspace/cloudberry-pxf/automation/src/test/resources/report.sql ${PXF_BASE_SERVERS}/database/",

                "mkdir -p ${PXF_BASE_SERVERS}/db-session-params",
                "cp ${TEMPLATES_DIR}/jdbc-site.xml ${PXF_BASE_SERVERS}/db-session-params/",
                "sed -i 's|YOUR_DATABASE_JDBC_DRIVER_CLASS_NAME|org.postgresql.Driver|' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_URL|jdbc:postgresql://localhost:7000/pxfautomation|' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_USER||' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_PASSWORD||' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",
                "sed -i 's|</configuration>|<property><name>jdbc.session.property.client_min_messages</name><value>debug1</value></property></configuration>|' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",
                "sed -i 's|</configuration>|<property><name>jdbc.session.property.default_statistics_target</name><value>123</value></property></configuration>|' ${PXF_BASE_SERVERS}/db-session-params/jdbc-site.xml",

                "mkdir -p ${PXF_BASE_SERVERS}/db-hive",
                "cp ${TEMPLATES_DIR}/jdbc-site.xml ${PXF_BASE_SERVERS}/db-hive/",
                "sed -i 's|YOUR_DATABASE_JDBC_DRIVER_CLASS_NAME|org.apache.hive.jdbc.HiveDriver|' ${PXF_BASE_SERVERS}/db-hive/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_URL|jdbc:hive2://localhost:10000/default|' ${PXF_BASE_SERVERS}/db-hive/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_USER||' ${PXF_BASE_SERVERS}/db-hive/jdbc-site.xml",
                "sed -i 's|YOUR_DATABASE_JDBC_PASSWORD||' ${PXF_BASE_SERVERS}/db-hive/jdbc-site.xml",
                "cp /home/gpadmin/workspace/cloudberry-pxf/automation/src/test/resources/hive-report.sql ${PXF_BASE_SERVERS}/db-hive/"
        );

        ExecResult result = container.execInContainer("bash", "-l", "-c", script);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    "JDBC server configuration failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }

        restartPxf();

        System.out.println("[PXFApplication] JDBC servers configured and PXF restarted");
    }

    public void restartPxf() throws IOException, InterruptedException {
        String script = String.join("\n",
                "set -e",
                "source " + SCRIPTS_PREFIX + "/pxf-env.sh",
                "$PXF_HOME/bin/pxf restart"
        );
        ExecResult result = container.execInContainer("bash", "-l", "-c", script);
        if (result.getExitCode() != 0) {
            throw new RuntimeException(
                    "PXF restart failed (exit " + result.getExitCode() + "):\n"
                            + result.getStdout() + "\n" + result.getStderr());
        }
        System.out.println("[PXFApplication] PXF restarted");
    }
}
