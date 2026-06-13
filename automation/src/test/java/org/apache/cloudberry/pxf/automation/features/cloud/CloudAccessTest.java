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

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.AbstractTestcontainersTest;
import org.apache.cloudberry.pxf.automation.SmallDataFactory;
import org.apache.cloudberry.pxf.automation.applications.PXFApplication;
import org.apache.cloudberry.pxf.automation.applications.S3Application;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.testcontainers.MinIOContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Functional CloudAccess Test
 */
@WorksWithFDW
public class CloudAccessTest extends AbstractTestcontainersTest {

    private static final String[] PXF_MULTISERVER_COLS = {
            "name text",
            "num integer",
            "dub double precision",
            "longNum bigint",
            "bool boolean"
    };

    private static final String[] PXF_WRITE_COLS = {
            "name text",
            "score integer"
    };

    private static final String fileName = "data.txt";

    private final SmallDataFactory dataFactory = new SmallDataFactory();

    private MinIOContainer s3Server;
    private S3Application s3Application;
    private String s3PathRead;
    private String s3PathWrite;
    private String readObjectKeyPrefix;
    private String writeObjectKeyPrefix;

    @Override
    public void beforeClass() throws Exception {
        s3Server = new MinIOContainer(container.getSharedNetwork());
        s3Server.start();
        s3Application = new S3Application(s3Server);
        s3Application.createBucket(MinIOContainer.DEFAULT_BUCKET);

        String random = UUID.randomUUID().toString();
        readObjectKeyPrefix = String.format("tmp/pxf_automation_data_read/%s/", random);
        writeObjectKeyPrefix = String.format("tmp/pxf_automation_data_write/%s/", random);
        s3PathRead = MinIOContainer.DEFAULT_BUCKET + "/" + readObjectKeyPrefix;
        s3PathWrite = MinIOContainer.DEFAULT_BUCKET + "/" + writeObjectKeyPrefix;
    }

    @Override
    public void afterClass() throws Exception {
        if (s3Application != null) {
            if (readObjectKeyPrefix != null) {
                s3Application.deletePrefix(MinIOContainer.DEFAULT_BUCKET, readObjectKeyPrefix);
            }
            if (writeObjectKeyPrefix != null) {
                s3Application.deletePrefix(MinIOContainer.DEFAULT_BUCKET, writeObjectKeyPrefix);
            }
            s3Application.shutdown();
        }
        if (s3Server != null) {
            s3Server.stop();
        }
    }

    @Override
    protected void beforeMethod() throws Exception {
        uploadSmallCsvFixture(MinIOContainer.DEFAULT_BUCKET, readObjectKeyPrefix + fileName);
    }

    // Uploads small CSV test data (see BaseTCFunctionality#getSmallData()) to the given S3 object.
    private void uploadSmallCsvFixture(String bucket, String objectKey) throws IOException {
        Path tempFile = dataFactory.writeTableToCsv(dataFactory.getSmallData(), ",");
        try {
            System.out.println("[CloudAccessTest] Uploading " + tempFile + " -> s3://" + bucket + "/" + objectKey);
            s3Application.putObject(bucket, objectKey, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    protected void afterMethod() throws Exception {
        if (s3Application != null) {
            s3Application.deletePrefix(MinIOContainer.DEFAULT_BUCKET, readObjectKeyPrefix);
            s3Application.deletePrefix(MinIOContainer.DEFAULT_BUCKET, writeObjectKeyPrefix);
        }
    }

    /*
     * The tests below are for the case where there's NO Hadoop cluster configured under "default" server
     * and assumes the "default" server has not configuration files. They are part of "s3" group and do not
     * make sense in the environment with Kerberized Hadoop, where the tests in the "security" group would run
     */

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessFailsWhenNoServerNoCredsSpecified() throws Exception {
        runTestScenario("no_server_no_credentials", null, false);
    }

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessFailsWhenServerNoCredsNoConfigFileExists() throws Exception {
        runTestScenario("server_no_credentials_no_config", "s3-non-existent", false);
    }

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessOkWhenNoServerCredsNoConfigFileExists() throws Exception {
        runTestScenario("no_server_credentials_no_config", null, true);
    }

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessFailsWhenServerNoCredsInvalidConfigFileExists() throws Exception {
        runTestScenario("server_no_credentials_invalid_config", "s3-invalid", false);
    }

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessOkWhenServerCredsInvalidConfigFileExists() throws Exception {
        runTestScenario("server_credentials_invalid_config", "s3-invalid", true);
    }

    @Test(enabled = false, groups = {"s3"})
    public void testCloudAccessOkWhenServerCredsNoConfigFileExists() throws Exception {
        runTestScenario("server_credentials_no_config", "s3-non-existent", true);
    }

    /*
     * The tests below are for the case where there's a Hadoop cluster configured under "default" server
     * both without and with Kerberos security, testing that cloud access works in presence of "default" server
     */

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsFailsWhenNoServerNoCredsSpecified() throws Exception {
        runTestScenario("no_server_no_credentials_with_hdfs", null, false);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsOkWhenServerNoCredsValidConfigFileExists() throws Exception {
        runTestScenario("server_no_credentials_valid_config_with_hdfs", "s3", false);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudWriteWithHdfsOkWhenServerNoCredsValidConfigFileExists() throws Exception {
        runTestScenarioForWrite("server_no_credentials_valid_config_with_hdfs_write", "s3", false);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsFailsWhenServerNoCredsNoConfigFileExists() throws Exception {
        runTestScenario("server_no_credentials_no_config_with_hdfs", "s3-non-existent", false);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsFailsWhenNoServerCredsNoConfigFileExists() throws Exception {
        runTestScenario("no_server_credentials_no_config_with_hdfs", null, true);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsFailsWhenServerNoCredsInvalidConfigFileExists() throws Exception {
        runTestScenario("server_no_credentials_invalid_config_with_hdfs", "s3-invalid", false);
    }

    @Test(groups = {"testcontainers", "pxf-s3"})
    public void testCloudAccessWithHdfsOkWhenServerCredsInvalidConfigFileExists() throws Exception {
        runTestScenario("server_credentials_invalid_config_with_hdfs", "s3-invalid", true);
    }

    private void runTestScenario(String name, String server, boolean creds) throws Exception {
        String tableName = "cloudaccess_" + name;
        ExternalTable exTable = TableFactory.getPxfReadableTextTable(tableName, PXF_MULTISERVER_COLS, s3PathRead + fileName, ",");
        exTable.setProfile("s3:text");
        String serverParam = (server == null) ? null : "server=" + server;
        exTable.setServer(serverParam);
        if (creds) {
            exTable.setUserParameters(new String[]{"accesskey=" + MinIOContainer.ACCESS_KEY, "secretkey=" + MinIOContainer.SECRET_KEY});
        }
        cloudberry.createTableAndVerify(exTable);

        regress.runSqlTest("features/cloud_access/" + name);
    }

    private void runTestScenarioForWrite(String name, String server, boolean creds) throws Exception {
        // create writable external table to write to S3
        String tableName = "cloudwrite_" + name;
        ExternalTable exTable = TableFactory.getPxfWritableTextTable(tableName, PXF_WRITE_COLS, s3PathWrite, ",");
        exTable.setProfile("s3:text");
        String serverParam = (server == null) ? null : "server=" + server;
        exTable.setServer(serverParam);
        if (creds) {
            exTable.setUserParameters(new String[]{"accesskey=" + MinIOContainer.ACCESS_KEY, "secretkey=" + MinIOContainer.SECRET_KEY});
        }
        cloudberry.createTableAndVerify(exTable);

        // create readable external table to read back from S3, making sure previous insert made it all the way to S3
        tableName = "cloudaccess_" + name;
        exTable = TableFactory.getPxfReadableTextTable(tableName, PXF_WRITE_COLS, s3PathWrite, ",");
        exTable.setProfile("s3:text");
        exTable.setServer(serverParam);
        if (creds) {
            exTable.setUserParameters(new String[]{"accesskey=" + MinIOContainer.ACCESS_KEY, "secretkey=" + MinIOContainer.SECRET_KEY});
        }
        cloudberry.createTableAndVerify(exTable);

        regress.runSqlTest("features/cloud_access/" + name);
    }
}
