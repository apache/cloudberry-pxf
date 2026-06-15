package org.apache.cloudberry.pxf.automation.features.cloud;

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

import org.apache.cloudberry.pxf.automation.AbstractTestcontainersTest;
import org.apache.cloudberry.pxf.automation.applications.S3Application;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ReadableExternalTable;
import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;
import org.apache.cloudberry.pxf.automation.utils.AutomationUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.apache.cloudberry.pxf.automation.features.tpch.LineItem.LINEITEM_SCHEMA;

/** Functional S3 Select Test */
public class S3SelectTest extends AbstractTestcontainersTest {

    private static final String[] PXF_S3_SELECT_INVALID_COLS = {
            "invalid_orderkey       BIGINT",
            "invalid_partkey        BIGINT",
            "invalid_suppkey        BIGINT",
            "invalid_linenumber     BIGINT",
            "invalid_quantity       DECIMAL(15,2)",
            "invalid_extendedprice  DECIMAL(15,2)",
            "invalid_discount       DECIMAL(15,2)",
            "invalid_tax            DECIMAL(15,2)",
            "invalid_returnflag     CHAR(1)",
            "invalid_linestatus     CHAR(1)",
            "invalid_shipdate       DATE",
            "invalid_commitdate     DATE",
            "invalid_receiptdate    DATE",
            "invalid_shipinstruct   CHAR(25)",
            "invalid_shipmode       CHAR(10)",
            "invalid_comment        VARCHAR(44)"
    };

    private MinIOContainer s3Server;
    private S3Application s3Application;
    private String s3Path;
    private String objectKeyPrefix;

    private static final String sampleCsvFile = "sample.csv";
    private static final String sampleGzippedCsvFile = "sample.csv.gz";
    private static final String sampleBzip2CsvFile = "sample.csv.bz2";
    private static final String sampleCsvNoHeaderFile = "sample-no-header.csv";
    private static final String sampleParquetFile = "sample.parquet";
    private static final String sampleParquetSnappyFile = "sample.snappy.parquet";
    private static final String sampleParquetGzipFile = "sample.gz.parquet";

    private static final String[] FIXTURE_FILES = {
            sampleCsvFile,
            sampleCsvNoHeaderFile,
            sampleGzippedCsvFile,
            sampleBzip2CsvFile,
            sampleParquetFile,
            sampleParquetSnappyFile,
            sampleParquetGzipFile,
    };

    private static final String FIXTURES_SUBDIR = "s3select";

    /**
     * Prepare all server configurations and components
     */
    @Override
    public void beforeClass() throws Exception {
        s3Server = new MinIOContainer(container.getSharedNetwork());
        s3Server.start();
        s3Application = new S3Application(s3Server);
        s3Application.createBucket(MinIOContainer.DEFAULT_BUCKET);

        String uuid = UUID.randomUUID().toString();
        objectKeyPrefix = "tmp/pxf_automation_data/" + uuid + "/s3select/";
        s3Path = MinIOContainer.DEFAULT_BUCKET + "/" + objectKeyPrefix;

        uploadFixtures(MinIOContainer.DEFAULT_BUCKET, objectKeyPrefix);
        // Server 's3' is pre-baked into the container image by entrypoint.sh.
    }

    @Override
    public void afterClass() throws Exception {
        if (s3Application != null && objectKeyPrefix != null) {
            s3Application.deletePrefix(MinIOContainer.DEFAULT_BUCKET, objectKeyPrefix);
            s3Application.shutdown();
        }
        if (s3Server != null) {
            s3Server.stop();
        }
    }

    // Uploads committed S3 Select fixture files from src/test/resources/data/s3select/ into MinIO.
    private void uploadFixtures(String bucket, String objectKeyPrefix) throws IOException {
        Path fixturesDir = resolveFixturesDirectory();
        for (String filename : FIXTURE_FILES) {
            Path localFile = fixturesDir.resolve(filename);
            if (!Files.isRegularFile(localFile)) {
                throw new IOException("Missing S3 Select fixture: " + localFile);
            }
            String key = objectKeyPrefix + filename;
            System.out.println("[S3SelectTest] Uploading " + localFile + " -> s3://" + bucket + "/" + key);
            s3Application.putObject(bucket, key, localFile);
        }
    }

    private static Path resolveFixturesDirectory() throws IOException {
        Path relative = Paths.get("src/test/resources/data", FIXTURES_SUBDIR);
        if (Files.isDirectory(relative)) {
            return relative.toAbsolutePath().normalize();
        }
        Path fromRepo = AutomationUtils.findRepoRoot()
                .resolve("automation/src/test/resources/data")
                .resolve(FIXTURES_SUBDIR);
        if (Files.isDirectory(fromRepo)) {
            return fromRepo;
        }
        throw new IOException("Cannot find s3select fixtures directory (tried "
                + relative.toAbsolutePath() + " and " + fromRepo + ")");
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testPlainCsvWithHeaders() throws Exception {
        String[] userParameters = {"FILE_HEADER=IGNORE", "S3_SELECT=ON"};
        runTestScenario("csv", "s3", "csv", sampleCsvFile, "|", userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testPlainCsvWithHeadersUsingHeaderInfo() throws Exception {
        String[] userParameters = {"FILE_HEADER=USE", "S3_SELECT=ON"};
        runTestScenario("csv_use_headers", "s3", "csv", sampleCsvFile, "|", userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCsvWithHeadersUsingHeaderInfoWithWrongColumnNames() throws Exception {
        String[] userParameters = {"FILE_HEADER=USE", "S3_SELECT=ON"};
        runTestScenario("errors/", "csv_use_headers_with_wrong_col_names", "s3", "csv",
                sampleCsvFile, "/" + s3Path + sampleCsvFile,
                "|", userParameters, PXF_S3_SELECT_INVALID_COLS);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testPlainCsvWithNoHeaders() throws Exception {
        String[] userParameters = {"FILE_HEADER=NONE", "S3_SELECT=ON"};
        runTestScenario("csv_noheaders", "s3", "csv", sampleCsvNoHeaderFile, "|", userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testGzipCsvWithHeadersUsingHeaderInfo() throws Exception {
        String[] userParameters = {"FILE_HEADER=USE", "S3_SELECT=ON", "COMPRESSION_CODEC=gzip"};
        runTestScenario("gzip_csv_use_headers", "s3", "csv", sampleGzippedCsvFile, "|", userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testBzip2CsvWithHeadersUsingHeaderInfo() throws Exception {
        String[] userParameters = {"FILE_HEADER=USE", "S3_SELECT=ON", "COMPRESSION_CODEC=bzip2"};
        runTestScenario("bzip2_csv_use_headers", "s3", "csv", sampleBzip2CsvFile, "|", userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testParquet() throws Exception {
        String[] userParameters = {"S3_SELECT=ON"};
        runTestScenario("parquet", "s3", "parquet", sampleParquetFile, null, userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testParquetWildcardLocation() throws Exception {
        String[] userParameters = {"S3_SELECT=ON"};
        runTestScenario("", "parquet", "s3", "parquet", sampleParquetFile, "/" + s3Path + "*e.parquet",
                null, userParameters, LINEITEM_SCHEMA);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testSnappyParquet() throws Exception {
        String[] userParameters = {"S3_SELECT=ON"};
        runTestScenario("parquet_snappy", "s3", "parquet", sampleParquetSnappyFile, null, userParameters);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testGzipParquet() throws Exception {
        String[] userParameters = {"S3_SELECT=ON"};
        runTestScenario("parquet_gzip", "s3", "parquet", sampleParquetGzipFile, null, userParameters);
    }

    private void runTestScenario(
            String name,
            String server,
            String format,
            String filename,
            String delimiter,
            String[] userParameters)
            throws Exception {
        runTestScenario("", name, server, format, filename, "/" + s3Path + filename,
                delimiter, userParameters, LINEITEM_SCHEMA);
    }

    private void runTestScenario(
            String qualifier,
            String name,
            String server,
            String format,
            String filename,
            String locationPath,
            String delimiter,
            String[] userParameters,
            String[] fields)
            throws Exception {
        String tableName = "s3select_" + name;
        ReadableExternalTable exTable = new ReadableExternalTable(tableName, fields, locationPath, "CSV");
        exTable.setProfile("s3:" + format);
        exTable.setServer("server=" + server);
        exTable.setHost(pxfHost);
        exTable.setPort(pxfPort);

        if (delimiter != null) {
            exTable.setDelimiter(delimiter);
        }
        if (userParameters != null) {
            exTable.setUserParameters(userParameters);
        }

        cloudberry.createTableAndVerify(exTable);
        regress.runSqlTest(String.format("features/s3_select/%s%s", qualifier, name));
    }
}
