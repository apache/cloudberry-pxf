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

public class PxfExtensionTest extends AbstractTestcontainersTest {

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
        extensionDb.runQuery("DROP EXTENSION IF EXISTS pxf CASCADE", true);
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfCreateExtension() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.1'");
        regress.runSqlTest("features/extension_tests/create_extension");
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfCreateExtensionOldRPM() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.0'");
        regress.runSqlTest("features/extension_tests/create_extension_rpm");
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfUpgrade() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.0'");
        regress.runSqlTest("features/extension_tests/upgrade/step_1_create_extension_with_older_pxf_version");

        extensionDb.runQuery("ALTER EXTENSION pxf UPDATE TO '2.1'");
        regress.runSqlTest("features/extension_tests/upgrade/step_2_after_alter_extension");
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfExplicitUpgrade() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.0'");
        regress.runSqlTest("features/extension_tests/explicit_upgrade/step_1_create_extension_with_older_pxf_version");

        extensionDb.runQuery("ALTER EXTENSION pxf UPDATE TO '2.1'");
        regress.runSqlTest("features/extension_tests/explicit_upgrade/step_2_after_alter_extension");
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfDowngrade() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.1'");
        regress.runSqlTest("features/extension_tests/downgrade/step_1_create_extension");

        extensionDb.runQuery("ALTER EXTENSION pxf UPDATE TO '2.0'");
        regress.runSqlTest("features/extension_tests/downgrade/step_2_after_alter_extension_downgrade");
    }

    @Test(groups = {"testcontainers", "pxfExtension"})
    public void testPxfDowngradeThenUpgradeAgain() throws Exception {
        extensionDb.runQuery("CREATE EXTENSION pxf VERSION '2.1'");
        regress.runSqlTest("features/extension_tests/downgrade_then_upgrade/step_1_check_extension");

        extensionDb.runQuery("ALTER EXTENSION pxf UPDATE TO '2.0'");
        regress.runSqlTest("features/extension_tests/downgrade_then_upgrade/step_2_after_alter_extension_downgrade");

        extensionDb.runQuery("ALTER EXTENSION pxf UPDATE TO '2.1'");
        regress.runSqlTest("features/extension_tests/downgrade_then_upgrade/step_3_after_alter_extension_upgrade");
    }
}
