package org.apache.cloudberry.pxf.automation.features.extension;

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
import org.apache.cloudberry.pxf.automation.applications.CloudberryApplication;
import org.testng.annotations.Test;

public class PxfFdwExtensionTest extends AbstractTestcontainersTest {

    private CloudberryApplication extensionDb;

    @Override
    protected void beforeClass() throws Exception {
        // pxf_regress scripts run "\c pxfautomation_extension", so the database must exist in the container.
        cloudberry.dropDatabase("pxfautomation_extension");
        cloudberry.createDatabase("pxfautomation_extension");

        extensionDb = new CloudberryApplication(container, "pxfautomation_extension");
        extensionDb.connect();
    }

    @Override
    protected void afterClass() throws Exception {
        if (extensionDb != null) {
            extensionDb.close();
        }
        cloudberry.dropDatabase("pxfautomation_extension");
    }

    @Override
    protected void beforeMethod() throws Exception {
        extensionDb.runQuery("DROP EXTENSION IF EXISTS pxf_fdw CASCADE", true);
    }

    @Test(groups = {"testcontainers", "pxf-fdw-extension"})
    public void testPxfCreateExtension() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf_fdw VERSION '2.0'");
        regress.runSqlTest("features/fdw_extension_tests/create_extension");
    }

    @Test(groups = {"testcontainers", "pxf-fdw-extension"})
    public void testPxfCreateExtensionOldRPM() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf_fdw VERSION '1.0'");
        regress.runSqlTest("features/fdw_extension_tests/create_extension_rpm");
    }

    @Test(groups = {"testcontainers", "pxf-fdw-extension"})
    public void testPxfUpgrade() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf_fdw VERSION '1.0'");
        regress.runSqlTest("features/fdw_extension_tests/upgrade/step_1_create_extension_with_older_pxf_version");

        extensionDb.runQuery("ALTER EXTENSION pxf_fdw UPDATE TO '2.0'");
        regress.runSqlTest("features/fdw_extension_tests/upgrade/step_2_after_alter_extension");
    }

    @Test(groups = {"testcontainers", "pxf-fdw-extension"})
    public void testPxfDowngradeThenUpgradeAgain() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf_fdw VERSION '2.0'");
        regress.runSqlTest("features/fdw_extension_tests/downgrade_then_upgrade/step_1_check_extension");

        extensionDb.runQuery("ALTER EXTENSION pxf_fdw UPDATE TO '1.0'");
        regress.runSqlTest("features/fdw_extension_tests/downgrade_then_upgrade/step_2_after_alter_extension_downgrade");

        extensionDb.runQuery("ALTER EXTENSION pxf_fdw UPDATE TO '2.0'");
        regress.runSqlTest("features/fdw_extension_tests/downgrade_then_upgrade/step_3_after_alter_extension_upgrade");
    }
}
