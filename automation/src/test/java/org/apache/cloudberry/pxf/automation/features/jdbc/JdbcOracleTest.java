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

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.AbstractTestcontainersTest;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.testcontainers.OracleContainer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

@WorksWithFDW
public class JdbcOracleTest extends AbstractTestcontainersTest {

    private static final String ORACLE_DRIVER = "oracle.jdbc.driver.OracleDriver";

    private static final String ORACLE_TABLE_READ = "pxf_types_read";
    private static final String ORACLE_TABLE_WRITE = "pxf_types_write";

    private static final String ORACLE_PXF_PATH_READ = "SYSTEM.pxf_types_read";
    private static final String ORACLE_PXF_PATH_WRITE = "SYSTEM.pxf_types_write";

    /** PXF external/foreign table column definitions */
    private static final String[] ORACLE_PXF_FIELDS = new String[] {
            "i_int int",
            "b_big bigint",
            "f_real real",
            "d_double double precision",
            "dec_num numeric",
            "t_text text",
            "d_date date",
            "d_ts timestamp"
    };

    private static final int V_I_INT = 1;
    private static final long V_B_BIG = 3L;
    private static final float V_F_REAL = 1.25f;
    private static final double V_D_DOUBLE = 3.1415926d;
    private static final String V_DEC_TEXT = "12345.6789012345";
    private static final String V_T_TEXT = "hello";
    private static final String V_D_DATE = "2020-01-02";
    private static final String V_D_TS = "2020-01-02 03:04:05.006";

    private OracleContainer oracleContainer;

    @Override
    public void beforeClass() throws Exception {
        oracleContainer = new OracleContainer(container.getSharedNetwork());
        oracleContainer.start();

        Assert.assertTrue(container.isRunning(), "PXFCloudberry container should be running");
        Assert.assertTrue(oracleContainer.isRunning(), "Oracle container should be running");
    }

    @Override
    public void afterClass() throws Exception {
        if (oracleContainer != null) {
            oracleContainer.stop();
        }
    }

    @Test(groups = {"testcontainers", "pxf-jdbc"})
    public void readSupportedTypes() throws Exception {
        createAndSeedOracleReadTable(oracleContainer.getJdbcUrl());

        ExternalTable pxfRead = TableFactory.getPxfJdbcReadableTable(
                "pxf_ora_oracle_read_types",
                ORACLE_PXF_FIELDS,
                ORACLE_PXF_PATH_READ,
                ORACLE_DRIVER,
                oracleContainer.getInternalJdbcUrl(),
                OracleContainer.ORACLE_USER,
                "PASS=" + OracleContainer.ORACLE_PASSWORD);
        pxfRead.setHost(pxfHost);
        pxfRead.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfRead);

        try {
            regress.runSqlTest("features/jdbc/oracle/read_types");
        } finally {
            cloudberry.dropTable(pxfRead, true);
        }
    }

    @Test(groups = {"testcontainers", "pxf-jdbc"})
    public void writeSupportedTypes() throws Exception {
        createOracleWriteTable(oracleContainer.getJdbcUrl());

        ExternalTable pxfWrite = TableFactory.getPxfJdbcWritableTable(
                "pxf_ora_oracle_write_types",
                ORACLE_PXF_FIELDS,
                ORACLE_PXF_PATH_WRITE,
                ORACLE_DRIVER,
                oracleContainer.getInternalJdbcUrl(),
                OracleContainer.ORACLE_USER,
                "PASS=" + OracleContainer.ORACLE_PASSWORD);
        pxfWrite.setHost(pxfHost);
        pxfWrite.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfWrite);

        ExternalTable pxfVerify = TableFactory.getPxfJdbcReadableTable(
                "pxf_ora_oracle_write_verify",
                ORACLE_PXF_FIELDS,
                ORACLE_PXF_PATH_WRITE,
                ORACLE_DRIVER,
                oracleContainer.getInternalJdbcUrl(),
                OracleContainer.ORACLE_USER,
                "PASS=" + OracleContainer.ORACLE_PASSWORD);
        pxfVerify.setHost(pxfHost);
        pxfVerify.setPort(pxfPort);
        cloudberry.createTableAndVerify(pxfVerify);

        try {
            regress.runSqlTest("features/jdbc/oracle/write_types");
        } finally {
            cloudberry.dropTable(pxfVerify, true);
            cloudberry.dropTable(pxfWrite, true);
        }
    }

    private void createAndSeedOracleReadTable(String jdbcUrl) throws SQLException {
        try (Connection conn = openOracleConnection(jdbcUrl)) {
            createOracleServerTable(conn, ORACLE_TABLE_READ);
            insertOracleReadFixture(conn);
        }
    }

    private void createOracleWriteTable(String jdbcUrl) throws SQLException {
        try (Connection conn = openOracleConnection(jdbcUrl)) {
            createOracleServerTable(conn, ORACLE_TABLE_WRITE);
        }
    }

    /** Creates Oracle table DROP if exists + CREATE */
    private void createOracleServerTable(Connection conn, String tableName) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Oracle does not support DROP TABLE IF EXISTS before 23c; use PL/SQL block
            st.execute("BEGIN"
                    + "  EXECUTE IMMEDIATE 'DROP TABLE " + tableName + " PURGE';"
                    + "EXCEPTION WHEN OTHERS THEN"
                    + "  IF SQLCODE != -942 THEN RAISE; END IF;"
                    + "END;");
            st.execute("CREATE TABLE " + tableName + " ("
                    + "i_int    NUMBER(10), "
                    + "b_big    NUMBER(19), "
                    + "f_real   BINARY_FLOAT, "
                    + "d_double BINARY_DOUBLE, "
                    + "dec_num  NUMBER(38,10), "
                    + "t_text   VARCHAR2(4000), "
                    + "d_date   DATE, "
                    + "d_ts     TIMESTAMP(3)"
                    + ")");
        }
    }

    /** Inserts fixture row into ORACLE_TABLE_READ for the read test */
    private void insertOracleReadFixture(Connection conn) throws SQLException {
        String insertSql = "INSERT INTO " + ORACLE_TABLE_READ + " ("
                + "i_int, b_big, f_real, d_double, dec_num, t_text, d_date, d_ts"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, V_I_INT);
            ps.setLong(2, V_B_BIG);
            ps.setFloat(3, V_F_REAL);
            ps.setDouble(4, V_D_DOUBLE);
            ps.setBigDecimal(5, new BigDecimal(V_DEC_TEXT));
            ps.setString(6, V_T_TEXT);
            ps.setDate(7, Date.valueOf(V_D_DATE));
            ps.setTimestamp(8, Timestamp.valueOf(V_D_TS));
            ps.executeUpdate();
        }
    }

    private Connection openOracleConnection(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, OracleContainer.ORACLE_USER, OracleContainer.ORACLE_PASSWORD);
    }
}
