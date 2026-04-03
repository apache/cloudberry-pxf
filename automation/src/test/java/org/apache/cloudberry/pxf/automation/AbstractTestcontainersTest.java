package org.apache.cloudberry.pxf.automation;

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

import listeners.CustomAutomationLogger;
import listeners.FDWSkipTestAnalyzer;
import org.apache.cloudberry.pxf.automation.applications.CloudberryApplication;
import org.apache.cloudberry.pxf.automation.applications.PXFApplication;
import org.apache.cloudberry.pxf.automation.applications.RegressApplication;
import org.apache.cloudberry.pxf.automation.testcontainers.PXFCloudberryContainer;
import org.apache.cloudberry.pxf.automation.utils.system.FDWUtils;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import reporters.CustomAutomationReport;

@Listeners({CustomAutomationLogger.class, CustomAutomationReport.class, FDWSkipTestAnalyzer.class})
public class AbstractTestcontainersTest {

    private static boolean sharedEnvironmentInitialized;

    protected final String pxfHost = "localhost";
    protected final String pxfPort = "5888";
    protected PXFCloudberryContainer container;
    protected CloudberryApplication cloudberry;
    protected RegressApplication regress;

    @BeforeClass(alwaysRun = true)
    public final void doInit() throws Exception {
        // redirect "doInit" logs to log file
        CustomAutomationLogger.redirectStdoutStreamToFile(getClass().getSimpleName(), "doInit");

        try {
            container = PXFCloudberryContainer.getInstance();

            try (CloudberryApplication bootstrap = new CloudberryApplication(container, "postgres")) {
                bootstrap.connect();
                createTestDatabases(bootstrap);
            }

            cloudberry = new CloudberryApplication(container);
            cloudberry.connect();
            cloudberry.createExtension("pxf", false);
            cloudberry.createExtension("pxf_fdw", false);

            if (!sharedEnvironmentInitialized) {
                // Ensure PXF JDBC server configs exist for SERVER=database/db-session-params tests.
                new PXFApplication(container).configureJdbcServers();
                if (FDWUtils.useFDW) {
                    cloudberry.createTestFDW(true);
                    cloudberry.createSystemFDW(true);
                    cloudberry.createForeignServers(true);
                }
                sharedEnvironmentInitialized = true;
            }

            regress = new RegressApplication(container);

            // run users before class
            beforeClass();
        } finally {
            CustomAutomationLogger.revertStdoutStream();
        }

    }

    @AfterClass(alwaysRun = true)
    public final void clean() throws Exception {
        if (ProtocolUtils.getPxfTestKeepData().equals("true")) {
            return;
        }
        CustomAutomationLogger.redirectStdoutStreamToFile(getClass().getSimpleName(), "clean");
        try {
            if (cloudberry != null) {
                cloudberry.close();
            }
        } finally {
            CustomAutomationLogger.revertStdoutStream();
        }
    }

    /**
     * clean up after the class finished
     *
     * @throws Exception
     */
    protected void afterClass() throws Exception {
    }

    /**
     * Preparations needed before the class starting
     *
     * @throws Exception
     */
    protected void beforeClass() throws Exception {
    }

    /**
     * clean up after the test method had finished
     *
     * @throws Exception
     */
    protected void afterMethod() throws Exception {
    }

    /**
     * Preparations needed before the test method starting
     *
     * @throws Exception
     */
    protected void beforeMethod() throws Exception {
    }


    private void createTestDatabases(CloudberryApplication bootstrap) throws Exception {
        bootstrap.createDatabase("pxfautomation");
        bootstrap.createDatabase("pxfautomation_encoding");
        bootstrap.runQuery("SELECT 1");
        System.out.println("[" + getClass().getSimpleName() + "] Test databases created");
    }
}
