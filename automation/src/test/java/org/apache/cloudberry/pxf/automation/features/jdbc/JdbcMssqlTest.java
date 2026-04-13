package org.apache.cloudberry.pxf.automation.features.jdbc;

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
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.testcontainers.MSSQLServerContainer;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;

public class JdbcMssqlTest extends AbstractTestcontainersTest {

    private static final String MSSQL_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private static final String MSSQL_TABLE_READ  = "pxf_types_read";
    private static final String MSSQL_TABLE_WRITE = "pxf_types_write";

    /** PXF external/foreign table column definitions — same for read and write tests. */
    private static final String[] MSSQL_PXF_FIELDS = new String[] {
            "i_int int",
            "s_small smallint",
            "b_big bigint",
            "f_float32 real",
            "d_float64 double precision",
            "b_bool boolean",
            "dec numeric",
            "t_text text",
            "bin bytea",
            "d_date date",
            "d_ts timestamp",
            "d_tstz timestamp with time zone"
    };

    private static final int    V_I_INT    = 1;
    private static final short  V_S_SMALL  = 2;
    private static final long   V_B_BIG    = 3L;
    private static final float  V_F_FLOAT32 = 1.25f;
    private static final double V_D_FLOAT64 = 3.1415926d;
    private static final boolean V_B_BOOL  = true;
    private static final String V_DEC_TEXT = "12345.6789012345";
    private static final String V_T_TEXT   = "hello";
    private static final String V_D_DATE   = "2020-01-02";
    private static final String V_D_TS     = "2020-01-02 03:04:05.006";

    private final String dockerImageTag;
    private MSSQLServerContainer mssqlContainer;

    /**
     * TestNG Factory: one test class instance per `mssqlVersions` row.
     */
    @Factory(dataProvider = "mssqlVersions")
    public static Object[] createInstances(String imageTag) {
        return new Object[] { new JdbcMssqlTest(imageTag) };
    }

    /** Docker image tags for MS SQL. */
    @DataProvider(name = "mssqlVersions")
    public static Object[][] mssqlVersions() {
        return new Object[][] {
                { "2019-latest" },
                { "2022-latest" },
        };
    }

    private JdbcMssqlTest(String dockerImageTag) {
        this.dockerImageTag = dockerImageTag;
    }

    @Override
    public void beforeClass() throws Exception {
        mssqlContainer = new MSSQLServerContainer(dockerImageTag, container.getSharedNetwork());
        mssqlContainer.start();

        Assert.assertTrue(container.isRunning(), "PXFCloudberry container should be running");
        Assert.assertTrue(mssqlContainer.isRunning(), "MSSQL container should be running");
    }

    @Override
    public void afterClass() throws Exception {
        if (mssqlContainer != null) {
            mssqlContainer.stop();
        }
    }

    @Test(groups = {"testcontainers", "pxf-jdbc"})
    public void readSupportedTypes() throws Exception {
        runReadSupportedTypes(mssqlContainer.getInternalJdbcUrl(), mssqlContainer.getJdbcUrl());
    }

    @Test(groups = {"testcontainers", "pxf-jdbc"})
    public void writeSupportedTypes() throws Exception {
        runWriteSupportedTypes(mssqlContainer.getInternalJdbcUrl(), mssqlContainer.getJdbcUrl());
    }

    private void runReadSupportedTypes(String internalJdbcUrl, String externalJdbcUrl) throws Exception {
        createAndSeedMssqlReadTable(externalJdbcUrl);

        ExternalTable pxfRead = TableFactory.getPxfJdbcReadableTable(
                "pxf_mssql_read_types",
                MSSQL_PXF_FIELDS,
                MSSQL_TABLE_READ,
                MSSQL_DRIVER,
                internalJdbcUrl,
                MSSQLServerContainer.MSSQL_USER,
                "PASS=" + MSSQLServerContainer.MSSQL_PASSWORD);
        pxfRead.setHost(pxfHost);
        pxfRead.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfRead);

        try {
            regress.runSqlTest("features/jdbc/mssql/read_types");
        } finally {
            cloudberry.dropTable(pxfRead, true);
        }
    }

    private void runWriteSupportedTypes(String internalJdbcUrl, String externalJdbcUrl) throws Exception {
        createMssqlWriteTable(externalJdbcUrl);

        ExternalTable pxfWrite = TableFactory.getPxfJdbcWritableTable(
                "pxf_mssql_write_types",
                MSSQL_PXF_FIELDS,
                MSSQL_TABLE_WRITE,
                MSSQL_DRIVER,
                internalJdbcUrl,
                MSSQLServerContainer.MSSQL_USER,
                "PASS=" + MSSQLServerContainer.MSSQL_PASSWORD);
        pxfWrite.setHost(pxfHost);
        pxfWrite.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfWrite);

        ExternalTable pxfVerify = TableFactory.getPxfJdbcReadableTable(
                "pxf_mssql_write_verify",
                MSSQL_PXF_FIELDS,
                MSSQL_TABLE_WRITE,
                MSSQL_DRIVER,
                internalJdbcUrl,
                MSSQLServerContainer.MSSQL_USER,
                "PASS=" + MSSQLServerContainer.MSSQL_PASSWORD);
        pxfVerify.setHost(pxfHost);
        pxfVerify.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfVerify);

        try {
            regress.runSqlTest("features/jdbc/mssql/write_types");
        } finally {
            cloudberry.dropTable(pxfVerify, true);
            cloudberry.dropTable(pxfWrite, true);
        }
    }

    private void createAndSeedMssqlReadTable(String jdbcUrl) throws SQLException {
        try (Connection conn = openMssqlConnection(jdbcUrl)) {
            createMssqlServerTable(conn, MSSQL_TABLE_READ);
            insertMssqlReadFixture(conn);
        }
    }

    private void createMssqlWriteTable(String jdbcUrl) throws SQLException {
        try (Connection conn = openMssqlConnection(jdbcUrl)) {
            createMssqlServerTable(conn, MSSQL_TABLE_WRITE);
        }
    }

    /** Creates MSSQL table (DROP IF EXISTS + CREATE). */
    private void createMssqlServerTable(Connection conn, String tableName) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("IF OBJECT_ID('dbo." + tableName + "', 'U') IS NOT NULL "
                    + "DROP TABLE dbo." + tableName);
            st.execute("CREATE TABLE dbo." + tableName + " ("
                    + "i_int        INT, "
                    + "s_small      SMALLINT, "
                    + "b_big        BIGINT, "
                    + "f_float32    REAL, "
                    + "d_float64    FLOAT, "
                    + "b_bool       BIT, "
                    + "dec          NUMERIC(38,10), "
                    + "t_text       NVARCHAR(1000), "
                    + "bin          VARBINARY(MAX), "
                    + "d_date       DATE, "
                    + "d_ts         DATETIME2(3), "
                    + "d_tstz       DATETIMEOFFSET(3)"
                    + ")");
        }
    }

    /** Inserts fixture row into `MSSQL_TABLE_READ` for the read test. */
    private void insertMssqlReadFixture(Connection conn) throws SQLException {
        String insertSql = "INSERT INTO dbo." + MSSQL_TABLE_READ + " ("
                + "i_int, s_small, b_big, f_float32, d_float64, b_bool, dec, t_text, bin, d_date, d_ts, d_tstz"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            ps.setInt(1, V_I_INT);
            ps.setShort(2, V_S_SMALL);
            ps.setLong(3, V_B_BIG);
            ps.setFloat(4, V_F_FLOAT32);
            ps.setDouble(5, V_D_FLOAT64);
            ps.setBoolean(6, V_B_BOOL);
            ps.setBigDecimal(7, new BigDecimal(V_DEC_TEXT));
            ps.setString(8, V_T_TEXT);
            ps.setBytes(9, new byte[]{0x41, 0x42, 0x43, 0x44});
            ps.setDate(10, Date.valueOf(V_D_DATE), utcCalendar);
            ps.setTimestamp(11, Timestamp.valueOf(V_D_TS), utcCalendar);
            ps.setTimestamp(12, Timestamp.valueOf(V_D_TS), utcCalendar);
            ps.executeUpdate();
        }
    }

    private Connection openMssqlConnection(String jdbcUrl) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", MSSQLServerContainer.MSSQL_USER);
        props.setProperty("password", MSSQLServerContainer.MSSQL_PASSWORD);
        return DriverManager.getConnection(jdbcUrl, props);
    }
}
