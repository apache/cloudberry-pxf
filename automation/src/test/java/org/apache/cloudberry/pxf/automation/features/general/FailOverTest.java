package org.apache.cloudberry.pxf.automation.features.general;

import annotations.FailsWithFDW;
import org.apache.cloudberry.pxf.automation.components.cluster.PhdCluster;
import org.apache.cloudberry.pxf.automation.features.BaseFeature;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ReadableExternalTable;
import org.testng.annotations.Test;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

/** Tests how failures are handled **/
@FailsWithFDW
public class FailOverTest extends BaseFeature {

    String testPackageLocation = "/org/apache/cloudberry/pxf/automation/testplugin/";
    String testPackage = "org.apache.cloudberry.pxf.automation.testplugin.";

    @Override
    protected void beforeClass() throws Exception {
        String newPath = "/tmp/publicstage/pxf";
        // copy additional plugins classes to cluster nodes, used for filter pushdown cases
        cluster.copyFileToNodes(new File("target/classes/" + testPackageLocation + "OutOfMemoryFragmenter.class").getAbsolutePath(), newPath + testPackageLocation, true, false);
        // add new path to classpath file and restart PXF service
        cluster.addPathToPxfClassPath(newPath);
        cluster.restart(PhdCluster.EnumClusterServices.pxf);
    }

    @Override
    protected void afterClass() throws Exception {
        super.afterClass();
        ensurePxfRunning();
    }

    /**
     * Should stop the JVM by invoking OutOfMemoryFragmenter
     *
     * @throws Exception
     */
    @Test(groups = {"features", "gpdb", "security"})
    public void stopTomcatOnOutOfMemory() throws Exception {

        // Create PXF external table for out of memory testing
        ReadableExternalTable pxfExternalTable = new ReadableExternalTable("test_out_of_memory", new String[] {
                "t0    text",
                "a1    integer",
                "b2    boolean",
                "colprojValue  text"
        }, "dummy_path","TEXT");

        pxfExternalTable.setFragmenter(testPackage + "OutOfMemoryFragmenter");
        pxfExternalTable.setAccessor("org.apache.cloudberry.pxf.plugins.hdfs.LineBreakAccessor");
        pxfExternalTable.setResolver("org.apache.cloudberry.pxf.plugins.hdfs.StringPassResolver");
        pxfExternalTable.setDelimiter(",");
        pxfExternalTable.setHost(pxfHost);
        pxfExternalTable.setPort(pxfPort);

        gpdb.createTableAndVerify(pxfExternalTable);

        runSqlTest("features/general/outOfMemory");

        // The test intentionally kills the PXF JVM; restart it for subsequent tests.
        ensurePxfRunning();
    }

    private void ensurePxfRunning() throws Exception {
        Integer port = parsePxfPort();
        if (cluster == null || port == null) {
            return;
        }

        String host = getPxfHttpHost();
        if (waitForPxfHealthy(host, port, 5_000)) {
            return;
        }

        // Wait for the OOM kill hook to fully stop the old process to avoid false positives
        // from jps/Bootstrap checks while the JVM is shutting down.
        waitForPortClosed(host, port, 60_000);

        for (int attempt = 1; attempt <= 3; attempt++) {
            cluster.restart(PhdCluster.EnumClusterServices.pxf);
            if (waitForPxfHealthy(host, port, 120_000)) {
                return;
            }
        }
        throw new RuntimeException("Failed to restart PXF after OutOfMemory test");
    }

    private Integer parsePxfPort() {
        if (pxfPort == null) {
            return null;
        }
        try {
            return Integer.parseInt(pxfPort);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getPxfHttpHost() {
        if (pxfHost == null || pxfHost.trim().isEmpty() || "0.0.0.0".equals(pxfHost.trim())) {
            return "localhost";
        }
        return pxfHost.trim();
    }

    private void waitForPortClosed(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!isPortOpen(host, port, 500)) {
                return;
            }
            Thread.sleep(500);
        }
    }

    private boolean waitForPxfHealthy(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isActuatorHealthy(host, port)) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isActuatorHealthy(String host, int port) {
        try {
            URL url = new URL(String.format("http://%s:%d/actuator/health", host, port));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            int code = connection.getResponseCode();
            connection.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        }
    }
}
