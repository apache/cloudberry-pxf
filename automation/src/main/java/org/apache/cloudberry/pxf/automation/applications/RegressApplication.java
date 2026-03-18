package org.apache.cloudberry.pxf.automation.applications;

import org.apache.cloudberry.pxf.automation.testcontainers.PXFCloudberryContainer;
import org.testcontainers.containers.Container.ExecResult;

/**
 * Runs {@code pxf_regress} SQL tests inside the TestContainers-managed container.
 * Replaces the SSH-based {@code Regress} system object.
 */
public class RegressApplication {

    private static final String REGRESS_DIR = "/home/gpadmin/workspace/cloudberry-pxf/automation/pxf_regress";
    private static final String SQL_REPO_DIR = "/home/gpadmin/workspace/cloudberry-pxf/automation/sqlrepo";
    private static final String DB_NAME = "pxfautomation";
    /** Written by {@code pxf_regress} under each test directory when comparisons fail. */
    private static final String REGRESSION_DIFFS_FILE = "regression.diffs";

    private final PXFCloudberryContainer container;

    public RegressApplication(PXFCloudberryContainer container) {
        this.container = container;
    }

    /**
     * Runs a SQL test using {@code pxf_regress} inside the container.
     *
     * @param sqlTestPath relative path under {@code sqlrepo/}, e.g. {@code "features/jdbc/single_fragment"}
     * @throws Exception if the test fails or the command errors out
     */
    public void runSqlTest(String sqlTestPath) throws Exception {
        System.out.println("[RegressApplication] Running SQL test: " + sqlTestPath);

        String command = String.join(" ",
                "cd " + SQL_REPO_DIR + " &&",
                "GPHOME=${GPHOME:-/usr/local/cloudberry-db}",
                "PATH=\"${GPHOME}/bin:$PATH\"",
                "PGHOST=localhost",
                "PGPORT=7000",
                "PGDATABASE=" + DB_NAME,
                REGRESS_DIR + "/pxf_regress",
                sqlTestPath);

        ExecResult result = container.execInContainer("bash", "-l", "-c", command);
        String output = result.getStdout();
        if (!output.isEmpty()) {
            System.out.println(output);
        }
        String errOutput = result.getStderr();
        if (errOutput != null && !errOutput.isEmpty()) {
            System.err.println(errOutput);
        }

        if (result.getExitCode() != 0) {
            printPxfRegressDiffsToStdout(sqlTestPath);
            throw new RuntimeException(
                    "pxf_regress FAILED for '" + sqlTestPath + "' (exit " + result.getExitCode() + "):\n" + output);
        }
        System.out.println("[RegressApplication] Test passed: " + sqlTestPath);
    }

    /**
     * Prints the aggregated diff file produced by {@code pxf_regress} (if present) to stdout.
     */
    private void printPxfRegressDiffsToStdout(String sqlTestPath) throws Exception {
        String diffsPath = SQL_REPO_DIR + "/" + sqlTestPath + "/" + REGRESSION_DIFFS_FILE;
        ExecResult cat = container.execInContainer("cat", diffsPath);
        String diffText = cat.getStdout();
        if (cat.getExitCode() == 0 && diffText != null && !diffText.isEmpty()) {
            System.out.println();
            System.out.println("===== pxf_regress " + REGRESSION_DIFFS_FILE + " =====");
            System.out.println(diffText);
            return;
        }
        System.out.println();
        System.out.println("[RegressApplication] No readable " + REGRESSION_DIFFS_FILE + " at " + diffsPath
                + " (cat exit " + cat.getExitCode() + ")");
    }
}