package org.apache.cloudberry.pxf.automation.features.parallel;

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.features.BaseFeature;
import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolEnum;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolUtils;
import org.testng.annotations.Test;

/**
 * Tests for PG-style Gather parallel scan on PXF FDW foreign tables.
 *
 * Verifies:
 * - Correctness: count, sum, ordering match between parallel and non-parallel scans
 * - EXPLAIN: Gather node present when parallel enabled, absent when disabled
 */
@WorksWithFDW
public class ParallelScanTest extends BaseFeature {

    private static final String[] FIELDS = {"id integer", "val text"};
    private static final int FILES = 10;
    private static final int ROWS_PER_FILE = 1000;
    private static final int SINGLE_FRAG_ROWS = 100;

    private String hdfsDir;
    private String singleFragDir;

    @Override
    protected void beforeClass() throws Exception {
        super.beforeClass();

        hdfsDir = hdfs.getWorkingDirectory() + "/parallel_data";
        hdfs.createDirectory(hdfsDir);

        // Write 10 CSV files to HDFS (10 x 1000 rows = 10,000 total)
        for (int fileIdx = 0; fileIdx < FILES; fileIdx++) {
            Table dataTable = new Table("part_" + String.format("%02d", fileIdx), null);
            int startId = fileIdx * ROWS_PER_FILE + 1;
            int endId = (fileIdx + 1) * ROWS_PER_FILE;
            for (int id = startId; id <= endId; id++) {
                dataTable.addRow(new String[]{String.valueOf(id), "row_" + id});
            }
            hdfs.writeTableToFile(hdfsDir + "/part_" + String.format("%02d", fileIdx) + ".csv",
                    dataTable, ",");
        }

        ProtocolEnum protocol = ProtocolUtils.getProtocol();
        String tablePath = protocol.getExternalTablePath(hdfs.getBasePath(), hdfsDir);

        // Create foreign table with enable_parallel=true
        exTable = TableFactory.getPxfReadableCSVTable("pxf_parallel_enabled",
                FIELDS, tablePath, ",");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        exTable.setUserParameters(new String[]{"enable_parallel=true"});
        gpdb.createTableAndVerify(exTable);

        // Create foreign table without enable_parallel (defaults to false)
        exTable = TableFactory.getPxfReadableCSVTable("pxf_parallel_disabled",
                FIELDS, tablePath, ",");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        gpdb.createTableAndVerify(exTable);

        // Write single CSV file to HDFS (1 file, 100 rows) for single-fragment tests
        singleFragDir = hdfs.getWorkingDirectory() + "/parallel_single_frag";
        hdfs.createDirectory(singleFragDir);

        Table singleTable = new Table("single_frag", null);
        for (int id = 1; id <= SINGLE_FRAG_ROWS; id++) {
            singleTable.addRow(new String[]{String.valueOf(id), "row_" + id});
        }
        hdfs.writeTableToFile(singleFragDir + "/data.csv", singleTable, ",");

        String singleFragPath = protocol.getExternalTablePath(hdfs.getBasePath(), singleFragDir);

        // Create foreign table for single-fragment with enable_parallel=true
        exTable = TableFactory.getPxfReadableCSVTable("pxf_parallel_single_frag",
                FIELDS, singleFragPath, ",");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        exTable.setUserParameters(new String[]{"enable_parallel=true"});
        gpdb.createTableAndVerify(exTable);
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelCorrectness() throws Exception {
        runSqlTest("features/parallel/correctness");
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelExplain() throws Exception {
        runSqlTest("features/parallel/explain");
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelSingleFragment() throws Exception {
        runSqlTest("features/parallel/single_fragment");
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelRescan() throws Exception {
        runSqlTest("features/parallel/rescan");
    }
}
