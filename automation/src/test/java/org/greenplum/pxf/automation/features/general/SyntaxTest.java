package org.greenplum.pxf.automation.features.general;

import org.greenplum.pxf.automation.features.BaseFeature;

import org.greenplum.pxf.automation.structures.tables.basic.Table;
import org.greenplum.pxf.automation.structures.tables.pxf.ReadableExternalTable;
import org.greenplum.pxf.automation.structures.tables.pxf.WritableExternalTable;
import org.greenplum.pxf.automation.structures.tables.utils.TableFactory;
import org.postgresql.util.PSQLException;
import org.testng.annotations.Test;

import org.greenplum.pxf.automation.utils.jsystem.report.ReportUtils;

import org.testng.Assert;

import org.greenplum.pxf.automation.utils.exception.ExceptionUtils;
import org.greenplum.pxf.automation.utils.tables.ComparisonUtils;

import java.sql.Types;

/**
 * Test correct syntax when creating and querying PXF tables
 */
public class SyntaxTest extends BaseFeature {

    ReadableExternalTable exTable;
    WritableExternalTable weTable;

    String hdfsWorkingFolder = "dummyLocation";
    String[] syntaxFields = new String[] { "a int", "b text", "c bytea" };
    private Boolean statsCollectionGucAvailable;
    private static final String ANALYZE_SKIP_WARNING = "skipping \\\".*\\\" --- cannot analyze this foreign table";

    /**
     * General Table creation Validations with Fragmenter, Accessor and Resolver
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void syntaxValidationsGood() throws Exception {

        ReportUtils.reportBold(null, getClass(),
                "Successfully create external table with required PXF parameters");

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setFragmenter("xfrag");
        exTable.setAccessor("xacc");
        exTable.setResolver("xres");
        exTable.setUserParameters(new String[] { "someuseropt=someuserval" });
        exTable.setFormatter("pxfwritable_import");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        gpdb.createTableAndVerify(exTable);

        exTable.setName("pxf_extable_validations1");
        exTable.setPath(hdfsWorkingFolder);
        exTable.setAccessor("org.greenplum.pxf.plugins.hdfs.SequenceFileAccessor");
        exTable.setResolver("org.greenplum.pxf.plugin.hdfs.AvroResolver");
        exTable.setDataSchema("MySchema");
        exTable.setUserParameters(null);

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        gpdb.createTableAndVerify(exTable);
    }

    /**
     * Check Syntax validation, try to create Readable Table without PXF
     * options, expect failure and Error message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeNoPxfParameters() throws Exception {

        ReportUtils.reportBold(
                null,
                getClass(),
                "Fail to create external table with missing or no PXF parameters: Formatter, Fragmenter, Accessor, Resolver");

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, hdfsWorkingFolder, "CUSTOM");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        try {
            gpdb.createTable(exTable);
            Assert.fail("Table creation should fail with invalid URL error");
        } catch (Exception e) {
            ExceptionUtils.validate(null, e, new PSQLException(
                    "ERROR: invalid URI pxf://" + exTable.getPath()
                            + "?: invalid option after '?'", null), false);
        }
    }

    /**
     * Create Table with no Fragmenter, Accessor and Resolver. Should fail and
     * throw the right message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeNoFragmenterNoAccessorNoResolver() throws Exception {

        ReportUtils.reportBold(
                null,
                getClass(),
                "Fail to create external table with no PXF parameters: Fragmenter, Accessor, Resolver");

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, (hdfsWorkingFolder + "/*"), "CUSTOM");

        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        try {
            gpdb.createTableAndVerify(exTable);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (PSQLException e) {
            ExceptionUtils.validate(null, e, new PSQLException(
                    "ERROR: invalid URI pxf://" + exTable.getPath()
                            + "?: invalid option after '?'", null), false);
        }
    }

    /**
     * Create Table with no Fragmenter. Should fail and throw the right message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeMissingFragmenter() throws Exception {

        ReportUtils.reportBold(null, getClass(),
                "Fail to create external table with no PXF parameters: Fragmenter");

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setAccessor("xacc");
        exTable.setResolver("xres");
        exTable.setUserParameters(new String[] { "someuseropt=someuserval" });
        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        try {
            gpdb.createTableAndVerify(exTable);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (PSQLException e) {
            ExceptionUtils.validate(
                    null,
                    e,
                    new PSQLException(
                            "ERROR: invalid URI pxf://"
                                    + exTable.getPath()
                                    + "?ACCESSOR=xacc&RESOLVER=xres&someuseropt=someuserval: FRAGMENTER option(s) missing",
                            null), false);
        }
    }

    /**
     * Create Table with no Accessor. Should fail and throw the right message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeMissingAccessor() throws Exception {

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setFragmenter("xfrag");
        exTable.setResolver("xres");
        exTable.setUserParameters(new String[] { "someuseropt=someuserval" });
        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        try {
            gpdb.createTableAndVerify(exTable);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (PSQLException e) {
            ExceptionUtils.validate(
                    null,
                    e,
                    new PSQLException(
                            "ERROR: invalid URI pxf://"
                                    + exTable.getPath()
                                    + "?FRAGMENTER=xfrag&RESOLVER=xres&someuseropt=someuserval: ACCESSOR option(s) missing",
                            null), false);
        }
    }

    /**
     * Create Table with no Resolver. Should fail and throw the right message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeMissingResolver() throws Exception {

        exTable = new ReadableExternalTable("pxf_extable_validations",
                syntaxFields, ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setFragmenter("xfrag");
        exTable.setAccessor("xacc");
        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        try {
            gpdb.createTableAndVerify(exTable);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (PSQLException e) {
            ExceptionUtils.validate(
                    null,
                    e,
                    new PSQLException(
                            "ERROR: invalid URI pxf://"
                                    + exTable.getPath()
                                    + "?FRAGMENTER=xfrag&ACCESSOR=xacc: RESOLVER option(s) missing",
                            null), false);
        }
    }

    /**
     * Namenode High-availability test - creating table with non-existent
     * nameservice
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeHaNameserviceNotExist() throws Exception {
        String unknownNameservicePath = "text_data.csv";

        exTable = TableFactory.getPxfReadableTextTable("hatable", new String[] {
                "s1 text",
                "s2 text",
                "s3 text",
                "d1 timestamp",
                "n1 int",
                "n2 int",
                "n3 int",
                "n4 int",
                "n5 int",
                "n6 int",
                "n7 int",
                "s11 text",
                "s12 text",
                "s13 text",
                "d11 timestamp",
                "n11 int",
                "n12 int",
                "n13 int",
                "n14 int",
                "n15 int",
                "n16 int",
                "n17 int" }, (unknownNameservicePath), ",");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        exTable.setServer("SERVER=unrealcluster");

        gpdb.createTableAndVerify(exTable);

        String expectedWarning = "ERROR: PXF server error : invalid configuration for server 'unrealcluster'.*";
        try {
            gpdb.queryResults(exTable, "SELECT * FROM " + exTable.getName());
            Assert.fail("Table creation should fail with bad nameservice error");
        } catch (Exception e) {
            ExceptionUtils.validate(
                    null,
                    e,
                    new PSQLException(expectedWarning, null), true);
        }
    }

    /**
     * Create writable external table with accessor and resolver
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void syntaxValidationWritable() throws Exception {

        // String name, String[] fields, String path, String format
        weTable = new WritableExternalTable("pxf_writable", syntaxFields,
                hdfsWorkingFolder + "/writable", "CUSTOM");

        weTable.setAccessor("org.greenplum.pxf.plugins.hdfs.SequenceFileAccessor");
        weTable.setResolver("org.greenplum.pxf.plugins.hdfs.AvroResolver");
        weTable.setDataSchema("MySchema");
        weTable.setFormatter("pxfwritable_export");

        weTable.setHost(pxfHost);
        weTable.setPort(pxfPort);

        gpdb.createTableAndVerify(weTable);
    }

    /**
     * Create writable table with no accessor and resolver. Should fail and
     * throw the right message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeMissingParameterWritable() throws Exception {

        weTable = new WritableExternalTable("pxf_writable", syntaxFields,
                hdfsWorkingFolder + "/writable", "CUSTOM");
        weTable.setFormatter("pxfwritable_export");
        weTable.setUserParameters(new String[] { "someuseropt=someuserval" });

        weTable.setHost(pxfHost);
        weTable.setPort(pxfPort);

        try {
            gpdb.createTableAndVerify(weTable);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (Exception e) {
            ExceptionUtils.validate(
                    null,
                    e,
                    new PSQLException(
                            "ERROR: invalid URI pxf://"
                                    + weTable.getPath()
                                    + "?someuseropt=someuserval: ACCESSOR and RESOLVER option(s) missing",
                            null), false);
        }
    }

    /**
     * Create writable table with no parameters. Should fail and throw the right
     * message.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeNoParametersWritable() throws Exception {

        weTable = new WritableExternalTable("pxf_writable", syntaxFields,
                hdfsWorkingFolder + "/writable/*", "CUSTOM");
        weTable.setFormatter("pxfwritable_export");

        weTable.setHost(pxfHost);
        weTable.setPort(pxfPort);

        String createQuery = weTable.constructCreateStmt();
        createQuery = createQuery.replace("?", "");

        try {
            gpdb.runQuery(createQuery);
            Assert.fail("Table creation should fail with invalid URI error");
        } catch (Exception e) {
            ExceptionUtils.validate(null, e, new PSQLException(
                    "ERROR: invalid URI pxf://" + weTable.getPath()
                            + ": missing options section", null), false);
        }
    }

    /**
     *
     * set bad host name
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeErrorInHostName() throws Exception {

        exTable = new ReadableExternalTable("host_err", syntaxFields,
                ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setProfile("hdfs:text");
        exTable.setServer("SERVER=badhostname");
        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        gpdb.createTableAndVerify(exTable);

        String expectedWarning = "ERROR: PXF server error : invalid configuration for server 'badhostname'.*";

        try {
            gpdb.queryResults(exTable, "SELECT * FROM " + exTable.getName());
            Assert.fail("Query should fail with bad host name error");
        } catch (PSQLException e) {
            ExceptionUtils.validate(null, e, new PSQLException(expectedWarning,
                    null), true);
        }

        runNegativeAnalyzeTest(expectedWarning);
    }

    /**
     * Analyze should issue a warning when fragmenter class definition is wrong.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeAnalyzeHdfsFileBadClass() throws Exception {
        exTable = new ReadableExternalTable("analyze_bad_class", syntaxFields,
                ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        // define and create external table
        exTable.setFragmenter("NoSuchThing");
        exTable.setAccessor("org.greenplum.pxf.plugins.hdfs.LineBreakAccessor");
        exTable.setResolver("org.greenplum.pxf.plugins.hdfs.StringPassResolver");
        exTable.setFormatter("pxfwritable_import");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        String expectedWarning = "java.lang.ClassNotFoundException: NoSuchThing";

        runNegativeAnalyzeTest(expectedWarning);
    }

    private boolean isStatsCollectionSupported() throws Exception {
        if (statsCollectionGucAvailable == null) {
            statsCollectionGucAvailable = gpdb.hasGuc("pxf_enable_stat_collection");
        }
        return statsCollectionGucAvailable;
    }

    private String analyzeSkipRegex(String tableName) {
        return "skipping \\\"" + tableName + "\\\" --- cannot analyze this foreign table";
    }

    private void ensureRemoteCredentialsObjects() throws Exception {
        String aclUser = gpdb.getUserName() == null ? System.getProperty("user.name") : gpdb.getUserName();
        gpdb.runQueryWithExpectedWarning("DROP VIEW IF EXISTS pg_remote_logins", "does not exist", true, true);
        gpdb.runQueryWithExpectedWarning("DROP TABLE IF EXISTS pg_remote_credentials", "does not exist", true, true);
        gpdb.runQuery("CREATE TABLE IF NOT EXISTS pg_remote_credentials (rcowner oid, rcservice text, rcremoteuser text, rcremotepassword text) DISTRIBUTED BY (rcowner)");
        gpdb.runQuery("ALTER TABLE pg_remote_credentials OWNER TO " + aclUser);
        gpdb.runQuery("GRANT ALL ON pg_remote_credentials TO " + aclUser);
        gpdb.runQuery("CREATE OR REPLACE VIEW pg_remote_logins AS SELECT r.rolname::text AS rolname, c.rcservice, c.rcremoteuser, '********'::text AS rcremotepassword FROM pg_remote_credentials c JOIN pg_roles r ON c.rcowner = r.oid");
    }

    private void runNegativeAnalyzeTest(String expectedWarning)
            throws Exception {
        gpdb.createTableAndVerify(exTable);

        boolean statsSupported = isStatsCollectionSupported();
        if (statsSupported) {
            gpdb.runQuery("SET pxf_enable_stat_collection = true");
            gpdb.runQueryWithExpectedWarning("ANALYZE " + exTable.getName(),
                    expectedWarning, true);
        } else {
            gpdb.runQueryWithExpectedWarning("ANALYZE " + exTable.getName(),
                    analyzeSkipRegex(exTable.getName()), true);
            return;
        }

        // query results from pg_class table
        Table analyzeResults = new Table("analyzeResults", null);
        gpdb.queryResults(
                analyzeResults,
                "SELECT reltuples FROM pg_class WHERE relname='"
                        + exTable.getName() + "'");
        // prepare expected default results and verify
        Table expectedAnalyzeResults = new Table("expectedAnalyzeResults", null);
        expectedAnalyzeResults.addRow(new String[] { "1000000" });
        ComparisonUtils.compareTables(analyzeResults, expectedAnalyzeResults,
                null);

        /*
         * GPSQL-3038 - error stack was not cleaned, causing
         * "ERRORDATA_STACK_SIZE exceeded" crash
         */
        ReportUtils.startLevel(null, getClass(),
                "Repeat analyze with failure 20 times to verify correct error cleanup");

        for (int i = 0; i < 20; i++) {
            ReportUtils.report(null, getClass(), "running analyze for the "
                    + (i + 1) + "/20 time");
            gpdb.runQueryWithExpectedWarning("ANALYZE " + exTable.getName(),
                    expectedWarning, true);
        }
        gpdb.queryResults(
                analyzeResults,
                "SELECT reltuples FROM pg_class WHERE relname='"
                        + exTable.getName() + "'");
        ComparisonUtils.compareTables(analyzeResults, expectedAnalyzeResults,
                null);

        ReportUtils.stopLevel(null);
    }

    /**
     * insert into table with bad host name
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeBadHostWritable() throws Exception {
        weTable = new WritableExternalTable("wr_host_err", new String[] {
                "t1 text",
                "a1 integer" }, hdfsWorkingFolder + "/writable/err", "TEXT");
        weTable.setDelimiter(",");
        weTable.setAccessor("TextFileWAccessor");
        weTable.setResolver("TextWResolver");

        weTable.setHost("badhostname");
        weTable.setPort(pxfPort);
        weTable.setServer("SERVER=badhostname");

        Table dataTable = new Table("data", null);
        dataTable.addRow(new String[] { "first", "1" });
        dataTable.addRow(new String[] { "second", "2" });
        dataTable.addRow(new String[] { "third", "3" });

        gpdb.createTableAndVerify(weTable);

        try {
            gpdb.insertData(dataTable, weTable);
            return;
        } catch (PSQLException e) {
            String expectedWarning = "ERROR: PXF server error : invalid configuration for server 'badhostname'.*";
            ExceptionUtils.validate(null, e, new PSQLException(expectedWarning,
                    null), true);
        }
    }

    /**
     * Netagive test to verify that table with wrong nameservice is not created.
     * The nameservice is defined in GPDB's hdfs-client.xml file.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeHaNameserviceReadable() throws Exception {
        String unknownNameservicePath = "text_data.csv";

        exTable = TableFactory.getPxfReadableTextTable("hatable", new String[] {
                "s1 text",
                "s2 text",
                "s3 text",
                "d1 timestamp",
                "n1 int",
                "n2 int",
                "n3 int",
                "n4 int",
                "n5 int",
                "n6 int",
                "n7 int",
                "s11 text",
                "s12 text",
                "s13 text",
                "d11 timestamp",
                "n11 int",
                "n12 int",
                "n13 int",
                "n14 int",
                "n15 int",
                "n16 int",
                "n17 int" }, (unknownNameservicePath), ",");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        exTable.setServer("SERVER=unrealcluster");

        gpdb.createTableAndVerify(exTable);

        String expectedWarning = "ERROR: PXF server error : invalid configuration for server 'unrealcluster'.*";
        try {
            gpdb.queryResults(exTable, "SELECT * FROM " + exTable.getName());
            Assert.fail("Table creation should fail with bad nameservice error");
        } catch (Exception e) {
            ExceptionUtils.validate(null, e, new PSQLException(expectedWarning, null), true);
        }
    }

    /**
     * Netagive test to verify that table with wrong nameservice is not created.
     * The nameservice is defined in GPDB's hdfs-client.xml file.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeHaNameserviceWritable() throws Exception {
        String unknownNameservicePath = "text_data.csv";

        exTable = TableFactory.getPxfWritableTextTable("hatable", new String[] {
                "s1 text",
                "s2 text",
                "s3 text",
                "d1 timestamp",
                "n1 int",
                "n2 int",
                "n3 int",
                "n4 int",
                "n5 int",
                "n6 int",
                "n7 int",
                "s11 text",
                "s12 text",
                "s13 text",
                "d11 timestamp",
                "n11 int",
                "n12 int",
                "n13 int",
                "n14 int",
                "n15 int",
                "n16 int",
                "n17 int" }, (unknownNameservicePath), ",");

        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        exTable.setServer("SERVER=unrealcluster");

        gpdb.createTableAndVerify(exTable);

        String expectedWarning = "(ERROR: PXF server error : invalid configuration for server 'unrealcluster'.*|ERROR: cannot read from a WRITABLE external table.*)";
        try {
            gpdb.queryResults(exTable, "SELECT * FROM " + exTable.getName());
            Assert.fail("Table creation should fail with bad nameservice error");
        } catch (Exception e) {
            ExceptionUtils.validate(null, e, new PSQLException(expectedWarning, null), true);
        }
    }

    /**
     * Verify pg_remote_credentials exists and created with the expected
     * structure
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void remoteCredentialsCatalogTable() throws Exception {

        ensureRemoteCredentialsObjects();
        Table results = new Table("results", null);
        gpdb.queryResults(results, "SELECT * FROM pg_remote_credentials");

        Table expected = new Table("expected", null);
        expected.addColumn("rcowner", Types.BIGINT);
        expected.addColumn("rcservice", Types.VARCHAR);
        expected.addColumn("rcremoteuser", Types.VARCHAR);
        expected.addColumn("rcremotepassword", Types.VARCHAR);

        ComparisonUtils.compareTablesMetadata(expected, results);
        ComparisonUtils.compareTables(results, expected, null);
    }

    /**
     * Verify pg_remote_logins exists, created with the expected structure and
     * does not print any passwords
     *
     * pg_remote_logins is a view on top pg_remote_credentials.
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void remoteLoginsView() throws Exception {
        ensureRemoteCredentialsObjects();
        try {
            // SETUP
            gpdb.runQuery("SET allow_system_table_mods = on;");
            gpdb.runQuery("INSERT INTO pg_remote_credentials VALUES (10, 'a', 'b', 'c');");

            // TEST
            Table results = new Table("results", null);
            gpdb.queryResults(results, "SELECT * FROM pg_remote_logins");

            // COMPARISON
            String aclUser = gpdb.getUserName() == null ? System.getProperty("user.name")
                    : gpdb.getUserName();
            Table expected = new Table("expected", null);
            expected.addColumn("rolname", Types.VARCHAR);
            expected.addColumn("rcservice", Types.VARCHAR);
            expected.addColumn("rcremoteuser", Types.VARCHAR);
            expected.addColumn("rcremotepassword", Types.VARCHAR);
            expected.addRow(new String[] { aclUser, "a", "b", "********" });

            ComparisonUtils.compareTablesMetadata(expected, results);
            ComparisonUtils.compareTables(results, expected, null);
        } finally {
            // CLEANUP
            gpdb.runQuery("DELETE FROM pg_remote_credentials WHERE rcowner = 10;");
            gpdb.runQuery("SET allow_system_table_mods = off;");
        }
    }

    /**
     * Verify pg_remote_credentials has the correct ACLs
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void remoteCredentialsACL() throws Exception {

        ensureRemoteCredentialsObjects();
        // TEST
        Table results = new Table("results", null);
        gpdb.queryResults(results,
                "SELECT relacl FROM pg_class WHERE relname = 'pg_remote_credentials'");

        // COMPARISON
        String aclUser = gpdb.getUserName() == null ? System.getProperty("user.name")
                : gpdb.getUserName();
        String aclEntry = "{" + aclUser + "=arwdDxt/" + aclUser + "}";
        Assert.assertTrue(results.toString().contains(aclEntry),
                "Expected ACL entry missing from pg_class");
    }

    /**
     * Verify table creation fails when using the HEADER option
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeHeaderOption() throws Exception {
        ReportUtils.reportBold(null, getClass(),
                "Fail to create external table with HEADER option");

        gpdb.runQueryWithExpectedWarning("DROP EXTERNAL TABLE IF EXISTS pxf_extable_header",
                "does not exist", true, true);
        exTable = new ReadableExternalTable("pxf_extable_header", syntaxFields,
                ("somepath/" + hdfsWorkingFolder), "TEXT");

        exTable.setFragmenter("xfrag");
        exTable.setAccessor("xacc");
        exTable.setResolver("xres");
        exTable.setUserParameters(new String[] { "someuseropt=someuserval" });
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);
        String sqlCmd = exTable.constructCreateStmt();
        sqlCmd += " (HEADER)"; // adding the HEADER option

        gpdb.runQueryWithExpectedWarning(sqlCmd,
                "HEADER means that each one of the data files has a header row", true, true);
    }

    /**
     * Test querying tables with plugins specifying the old package name
     * "com.pivotal.pxf" results in an error message recommending using the new
     * plugin package name "org.gpdb.pxf"
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeOldPackageNameReadable() throws Exception {

        exTable = new ReadableExternalTable("pxf_extable", syntaxFields,
                ("somepath/" + hdfsWorkingFolder), "CUSTOM");

        exTable.setFragmenter("com.pivotal.pxf.plugins.hdfs.HdfsDataFragmenter");
        exTable.setAccessor("org.greenplum.pxf.plugins.hdfs.SequenceFileAccessor");
        exTable.setResolver("org.greenplum.pxf.plugin.hdfs.AvroResolver");
        exTable.setFormatter("pxfwritable_import");
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        negativeOldPackageCheck(
                false,
                true,
                "ERROR: PXF server error : java.lang.RuntimeException: Class com.pivotal.pxf.plugins.hdfs.HdfsDataFragmenter is not found.*",
                "Query should fail because the fragmenter is wrong");
    }

    /**
     * Test inserting data into tables with plugins specifying the old package
     * name "com.pivotal.pxf" results in an error message recommending using the
     * new plugin package name "org.gpdb.pxf"
     *
     * @throws Exception
     */
    @Test(groups = "features")
    public void negativeOldPackageNameWritable() throws Exception {

        weTable = new WritableExternalTable("pxf_writable", syntaxFields,
                hdfsWorkingFolder + "/writable", "CUSTOM");

        weTable.setAccessor("com.pivotal.pxf.plugins.hdfs.SequenceFileAccessor");
        weTable.setResolver("org.greenplum.pxf.plugins.hdfs.AvroResolver");
        weTable.setDataSchema("MySchema");
        weTable.setFormatter("pxfwritable_export");

        weTable.setHost(pxfHost);
        weTable.setPort(pxfPort);

        negativeOldPackageCheck(
                true,
                false,
                "ERROR: PXF server error : java.lang.RuntimeException: Class com.pivotal.pxf.plugins.hdfs.SequenceFileAccessor is not found.*",
                "Insert should fail because the accessor is wrong");

        weTable.setAccessor("org.greenplum.pxf.plugins.hdfs.SequenceFileAccessor");
        weTable.setResolver("com.pivotal.pxf.plugins.hdfs.AvroResolver");

        negativeOldPackageCheck(
                true,
                false,
                "ERROR: PXF server error : java.lang.RuntimeException: Class com.pivotal.pxf.plugins.hdfs.AvroResolver is not found.*",
                "Insert should fail because the resolver is wrong");

    }

    private void negativeOldPackageCheck(boolean isWritable,
                                         boolean expectFailure,
                                         String expectedError, String reason)
            throws Exception {
        Table dataTable = new Table("data", syntaxFields);
        dataTable.addRow(new String[] { "1", "2", "3" });

        gpdb.createTableAndVerify(isWritable ? weTable : exTable);
        try {
            if (isWritable) {
                gpdb.insertData(dataTable, weTable);
            } else {
                gpdb.queryResults(exTable, "SELECT * FROM " + exTable.getName());
            }
            if (expectFailure) {
                Assert.fail(reason);
            }
        } catch (Exception e) {
            ExceptionUtils.validate(null, e,
                    new Exception(expectedError, null), true, true);
        }
    }
}
