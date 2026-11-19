package org.apache.cloudberry.pxf.automation.features.parallel;

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

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.features.BaseFeature;
import org.apache.cloudberry.pxf.automation.structures.tables.basic.Table;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolEnum;
import org.apache.cloudberry.pxf.automation.utils.system.FDWUtils;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolUtils;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Tests for Cloudberry gang-parallel scan on PXF FDW foreign tables.
 *
 * Verifies:
 * - Correctness: count, sum, ordering match between parallel and non-parallel scans
 * - EXPLAIN: Parallel Foreign Scan present when parallel enabled, absent when disabled
 * - Parallel Append: UNION ALL and partitioned tables return every row
 * - Option precedence: table-level enable_parallel overrides the server level
 * - Reject limit: a table with a segment reject limit is not scanned in parallel
 */
@WorksWithFDW
public class ParallelScanTest extends BaseFeature {

    private static final String[] FIELDS = {"id integer", "val text"};
    private static final int FILES = 10;
    private static final int ROWS_PER_FILE = 1000;
    private static final int SINGLE_FRAG_ROWS = 100;
    private static final int SECOND_PART_FILES = 4;
    private static final int BAD_ROW_FILES = 12;
    private static final int BAD_FILE_ROWS = 100;

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

        if (FDWUtils.useFDW) {
            createPlanFunctions();
            createAppendObjects(protocol, tablePath);
            createOptionPrecedenceObjects(protocol, tablePath);
            createRejectLimitObjects(protocol);
        }
    }

    /**
     * Plan helpers that return EXPLAIN output as query results:
     * pxf_parallel_scan_count counts the Parallel Foreign Scan nodes, and
     * pxf_parallel_plan returns the plan lines with the number of segments
     * and rows masked. The statements in the sql files must not contain
     * "EXPLAIN ... SELECT": atmsort.pl replaces the output of such statements
     * with the output of explain.pl, which is empty when explain.pl is not
     * installed, so the plans would not be compared at all.
     */
    private void createPlanFunctions() throws Exception {
        gpdb.runQuery("CREATE OR REPLACE FUNCTION pxf_parallel_scan_count(q text) RETURNS int AS $$"
                + " DECLARE r text; n int := 0;"
                + " BEGIN"
                + "   FOR r IN EXECUTE 'EXPLAIN (COSTS OFF) ' || q LOOP"
                + "     IF r LIKE '%Parallel Foreign Scan%' THEN n := n + 1; END IF;"
                + "   END LOOP;"
                + "   RETURN n;"
                + " END $$ LANGUAGE plpgsql");

        gpdb.runQuery("CREATE OR REPLACE FUNCTION pxf_parallel_plan(opts text, q text) RETURNS SETOF text AS $$"
                + " DECLARE r text;"
                + " BEGIN"
                + "   FOR r IN EXECUTE 'EXPLAIN (' || opts || ') ' || q LOOP"
                + "     r := regexp_replace(r, 'Gather Motion \\d+:1  \\(slice\\d+; segments: \\d+\\)',"
                + "                            'Gather Motion N:1  (sliceN; segments: N)');"
                + "     RETURN NEXT regexp_replace(r, 'actual rows=\\d+ loops=\\d+', 'actual rows=N loops=N');"
                + "   END LOOP;"
                + " END $$ LANGUAGE plpgsql");
    }

    /**
     * Objects for Parallel Append cases: a partitioned table with two foreign
     * partitions (ids 1-10000 and 10001-14000).
     */
    private void createAppendObjects(ProtocolEnum protocol, String tablePath) throws Exception {
        String secondDir = hdfs.getWorkingDirectory() + "/parallel_data_2";
        hdfs.createDirectory(secondDir);
        for (int fileIdx = 0; fileIdx < SECOND_PART_FILES; fileIdx++) {
            Table dataTable = new Table("part2_" + fileIdx, null);
            int startId = FILES * ROWS_PER_FILE + fileIdx * ROWS_PER_FILE + 1;
            for (int id = startId; id < startId + ROWS_PER_FILE; id++) {
                dataTable.addRow(new String[]{String.valueOf(id), "row_" + id});
            }
            hdfs.writeTableToFile(secondDir + "/part_" + fileIdx + ".csv", dataTable, ",");
        }
        String secondPath = protocol.getExternalTablePath(hdfs.getBasePath(), secondDir);

        // runQuery fails on any NOTICE, so ignore the one DROP ... IF EXISTS raises
        gpdb.runQuery("DROP TABLE IF EXISTS pxf_parallel_partitioned CASCADE", true, false);
        gpdb.runQuery("CREATE TABLE pxf_parallel_partitioned (id integer, val text) PARTITION BY RANGE (id)"
                + " DISTRIBUTED BY (id)");
        gpdb.runQuery("CREATE FOREIGN TABLE pxf_parallel_partitioned_1 PARTITION OF pxf_parallel_partitioned"
                + " FOR VALUES FROM (1) TO (10001)"
                + serverAndOptions("pxf_parallel_partitioned_1", tablePath, "enable_parallel=true"));
        gpdb.runQuery("CREATE FOREIGN TABLE pxf_parallel_partitioned_2 PARTITION OF pxf_parallel_partitioned"
                + " FOR VALUES FROM (10001) TO (14001)"
                + serverAndOptions("pxf_parallel_partitioned_2", secondPath, "enable_parallel=true"));
    }

    /**
     * Objects for option precedence: enable_parallel set on the server and
     * overridden (or not) on the table.
     */
    private void createOptionPrecedenceObjects(ProtocolEnum protocol, String tablePath) throws Exception {
        String fdw = protocol.value() + "_pxf_fdw";
        createServer("pxf_parallel_srv_on", fdw, "true");
        createServer("pxf_parallel_srv_off", fdw, "false");

        createTableOnServer("pxf_parallel_srv_on_tbl_unset", "pxf_parallel_srv_on", tablePath);
        createTableOnServer("pxf_parallel_srv_on_tbl_off", "pxf_parallel_srv_on", tablePath,
                "enable_parallel=false");
        createTableOnServer("pxf_parallel_srv_off_tbl_on", "pxf_parallel_srv_off", tablePath,
                "enable_parallel=true");
    }

    /**
     * Objects for the reject limit cases: 12 files of 100 rows, each with one
     * malformed row, read through tables with a segment reject limit of 3
     * rows, which up to 3 segments exceed, and of 20 rows, which no segment
     * can exceed.
     */
    private void createRejectLimitObjects(ProtocolEnum protocol) throws Exception {
        String badDir = hdfs.getWorkingDirectory() + "/parallel_bad_rows";
        hdfs.createDirectory(badDir);
        for (int fileIdx = 0; fileIdx < BAD_ROW_FILES; fileIdx++) {
            Table dataTable = new Table("bad_" + fileIdx, null);
            int startId = fileIdx * BAD_FILE_ROWS + 1;
            for (int id = startId; id < startId + BAD_FILE_ROWS; id++) {
                dataTable.addRow(new String[]{String.valueOf(id), "row_" + id});
            }
            dataTable.addRow(new String[]{"bad", "row"});
            hdfs.writeTableToFile(badDir + "/part_" + String.format("%02d", fileIdx) + ".csv", dataTable, ",");
        }
        String badPath = protocol.getExternalTablePath(hdfs.getBasePath(), badDir);

        for (int limit : new int[]{3, 20}) {
            exTable = TableFactory.getPxfReadableCSVTable("pxf_parallel_reject_" + limit,
                    FIELDS, badPath, ",");
            exTable.setHost(pxfHost);
            exTable.setPort(pxfPort);
            exTable.setUserParameters(new String[]{"enable_parallel=true"});
            exTable.setSegmentRejectLimitAndType(limit, "ROWS");
            gpdb.createTableAndVerify(exTable);
        }
    }

    private void createServer(String name, String fdw, String enableParallel) throws Exception {
        gpdb.runQuery("DROP SERVER IF EXISTS " + name + " CASCADE", true, false);
        gpdb.runQuery(String.format("CREATE SERVER %s FOREIGN DATA WRAPPER %s"
                + " OPTIONS (config 'default', enable_parallel '%s')", name, fdw, enableParallel));
        gpdb.runQuery("CREATE USER MAPPING FOR CURRENT_USER SERVER " + name);
    }

    private void createTableOnServer(String name, String server, String path, String... params)
            throws Exception {
        String stmt = serverAndOptions(name, path, params);
        gpdb.runQuery("CREATE FOREIGN TABLE " + name + " (id integer, val text) SERVER " + server
                + stmt.substring(stmt.indexOf(" OPTIONS (")));
    }

    /** Returns the " SERVER ... OPTIONS (...)" part of a readable CSV foreign table definition. */
    private String serverAndOptions(String name, String path, String... params) {
        ExternalTable table = TableFactory.getPxfReadableCSVTable(name, FIELDS, path, ",");
        table.setHost(pxfHost);
        table.setPort(pxfPort);
        if (params.length > 0) {
            table.setUserParameters(params);
        }
        String stmt = table.constructCreateStmt();
        return stmt.substring(stmt.indexOf(" SERVER "));
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelCorrectness() throws Exception {
        runSqlTest("features/parallel/correctness");
    }

    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelExplain() throws Exception {
        skipIfNotFDW();
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

    /**
     * A Parallel Append runs each child in only some of the workers, so a
     * parallel foreign scan must not be planned under it. UNION ALL and
     * partitioned tables must return every row.
     */
    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelAppend() throws Exception {
        skipIfNotFDW();
        runSqlTest("features/parallel/append");
    }

    /** Table-level enable_parallel overrides the server-level value. */
    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelOptionPrecedence() throws Exception {
        skipIfNotFDW();
        runSqlTest("features/parallel/option_precedence");
    }

    /**
     * The segment reject limit is counted per process, so a table with a
     * reject limit must not be scanned by several gang workers per segment.
     */
    @Test(groups = {"features", "gpdb", "fdw"})
    public void testParallelRejectLimit() throws Exception {
        skipIfNotFDW();
        runSqlTest("features/parallel/reject_limit");
    }

    private void skipIfNotFDW() {
        if (!FDWUtils.useFDW) {
            throw new SkipException("parallel scan is supported only by pxf_fdw");
        }
    }
}
