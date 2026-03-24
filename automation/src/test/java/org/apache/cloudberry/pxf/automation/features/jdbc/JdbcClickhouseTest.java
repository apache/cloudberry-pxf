package org.apache.cloudberry.pxf.automation.features.jdbc;

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.AbstractTestcontainersTest;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.testcontainers.ClickHouseContainer;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.UUID;

@WorksWithFDW
public class JdbcClickhouseTest extends AbstractTestcontainersTest {

    private static final String CLICKHOUSE_DRIVER = "com.clickhouse.jdbc.ClickHouseDriver";

    private static final String CLICKHOUSE_TABLE_READ = "pxf_types_read";
    private static final String CLICKHOUSE_TABLE_WRITE = "pxf_types_write";

    // PXF protocol compression options passed via LOCATION user parameters.
    private static final String PROTOCOL_COMPRESS_ENABLED = "compress=true";
    private static final String PROTOCOL_COMPRESS_ALGORITHM_LZ4 = "compress_algorithm=lz4";
    private static final String HTTP_CONNECTION_PROVIDER_APACHE = "http_connection_provider=APACHE_HTTP_CLIENT";

    /** PXF external/foreign table column definitions — same for read and write tests. */
    private static final String[] CLICKHOUSE_PXF_FIELDS = new String[] {
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
            "d_tstz timestamp with time zone",
            "d_uuid uuid"
    };

    private static final int V_I_INT = 1;
    private static final short V_S_SMALL = 2;
    private static final long V_B_BIG = 3L;
    private static final float V_F_FLOAT32 = 1.25f;
    private static final double V_D_FLOAT64 = 3.1415926d;
    private static final boolean V_B_BOOL = true;
    private static final String V_DEC_TEXT = "12345.6789012345";
    private static final String V_T_TEXT = "hello";
    private static final String V_D_DATE = "2020-01-02";
    private static final String V_D_TS = "2020-01-02 03:04:05.006";
    private static final String V_D_UUID = "550e8400-e29b-41d4-a716-446655440000";
    /** Four-byte payload for binary data; same bytes as `decode('41424344','hex')` in regress SQL. */
    private static final byte[] V_BIN_BYTES = "ABCD".getBytes(StandardCharsets.US_ASCII);

    private final String dockerImageTag;

    private ClickHouseContainer clickhouseContainer;

    /**
     * TestNG {@link Factory}: one test class instance per {@link #clickhouseVersions()} row (separate ClickHouse container).
     */
    @Factory(dataProvider = "clickhouseVersions")
    public static Object[] createInstances(String imageTag) {
        return new Object[] { new JdbcClickhouseTest(imageTag) };
    }

    /** Docker image tags for {@code clickhouse/clickhouse-server:&lt;tag&gt;} — same regress SQL for both. */
    @DataProvider(name = "clickhouseVersions")
    public static Object[][] clickhouseVersions() {
        return new Object[][] {
                { "24" },
                { "26.1.4.35" },
                { "26.2" },
        };
    }

    private JdbcClickhouseTest(String dockerImageTag) {
        this.dockerImageTag = dockerImageTag;
    }

    @Override
    public void beforeClass() throws Exception {
        clickhouseContainer = new ClickHouseContainer(dockerImageTag, container.getSharedNetwork());
        clickhouseContainer.start();

        Assert.assertTrue(container.isRunning(), "PXFCloudberry container should be running");
        Assert.assertTrue(clickhouseContainer.isRunning(), "ClickHouse container should be running");
    }

    @Override
    public void afterClass() throws Exception {
        if (clickhouseContainer != null) {
            clickhouseContainer.stop();
        }
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void readSupportedTypes() throws Exception {
        runReadSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), false);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void readSupportedTypesWithProtocolCompression() throws Exception {
        runReadSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), true);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void readSupportedTypesWithHttpConnectionProvider() throws Exception {
        runReadSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), false, true);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void writeSupportedTypes() throws Exception {
        runWriteSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), false);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void writeSupportedTypesWithProtocolCompression() throws Exception {
        runWriteSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), true);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void writeSupportedTypesWithHttpConnectionProvider() throws Exception {
        runWriteSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), false, true);
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void writeSupportedTypesWithHttpConnectionProviderAndCompression() throws Exception {
        runWriteSupportedTypes(clickhouseContainer.getInternalJdbcUrl(), clickhouseContainer.getJdbcUrl(), true, true);
    }

    private void runReadSupportedTypes(String internalJdbcUrl, String externalJdbcUrl, boolean enableProtocolCompression) throws Exception {
        runReadSupportedTypes(internalJdbcUrl, externalJdbcUrl, enableProtocolCompression, false);
    }

    private void runReadSupportedTypes(String internalJdbcUrl, String externalJdbcUrl, boolean enableProtocolCompression, boolean enableHttpConnectionProvider) throws Exception {
        createAndSeedClickhouseReadTable(externalJdbcUrl);

        ExternalTable pxfRead = TableFactory.getPxfJdbcReadableTable(
                "pxf_ch_clickhouse_read_types",
                CLICKHOUSE_PXF_FIELDS,
                CLICKHOUSE_TABLE_READ,
                CLICKHOUSE_DRIVER,
                internalJdbcUrl,
                ClickHouseContainer.CLICKHOUSE_USER,
                "PASS=" + ClickHouseContainer.CLICKHOUSE_PASSWORD);
        pxfRead.setHost(pxfHost);
        pxfRead.setPort(pxfPort);
        if (enableProtocolCompression) {
            pxfRead.addUserParameter(PROTOCOL_COMPRESS_ENABLED);
            pxfRead.addUserParameter(PROTOCOL_COMPRESS_ALGORITHM_LZ4);
        }
        if (enableHttpConnectionProvider) {
            pxfRead.addUserParameter(HTTP_CONNECTION_PROVIDER_APACHE);
        }
        cloudberry.createTableAndVerify(pxfRead);

        try {
            regress.runSqlTest("features/jdbc/clickhouse/read_types");
        } finally {
            cloudberry.dropTable(pxfRead, true);
        }
    }

    private void runWriteSupportedTypes(String internalJdbcUrl, String externalJdbcUrl, boolean enableProtocolCompression) throws Exception {
        runWriteSupportedTypes(internalJdbcUrl, externalJdbcUrl, enableProtocolCompression, false);
    }

    private void runWriteSupportedTypes(String internalJdbcUrl, String externalJdbcUrl, boolean enableProtocolCompression, boolean enableHttpConnectionProvider) throws Exception {
        createClickhouseWriteTable(externalJdbcUrl);

        ExternalTable pxfWrite = TableFactory.getPxfJdbcWritableTable(
                "pxf_ch_clickhouse_write_types",
                CLICKHOUSE_PXF_FIELDS,
                CLICKHOUSE_TABLE_WRITE,
                CLICKHOUSE_DRIVER,
                internalJdbcUrl,
                ClickHouseContainer.CLICKHOUSE_USER,
                "PASS=" + ClickHouseContainer.CLICKHOUSE_PASSWORD);
        pxfWrite.setHost(pxfHost);
        pxfWrite.setPort(pxfPort);
        if (enableProtocolCompression) {
            pxfWrite.addUserParameter(PROTOCOL_COMPRESS_ENABLED);
            pxfWrite.addUserParameter(PROTOCOL_COMPRESS_ALGORITHM_LZ4);
        }
        if (enableHttpConnectionProvider) {
            pxfWrite.addUserParameter(HTTP_CONNECTION_PROVIDER_APACHE);
        }
        cloudberry.createTableAndVerify(pxfWrite);

        ExternalTable pxfVerify = TableFactory.getPxfJdbcReadableTable(
                "pxf_ch_clickhouse_write_verify",
                CLICKHOUSE_PXF_FIELDS,
                CLICKHOUSE_TABLE_WRITE,
                CLICKHOUSE_DRIVER,
                internalJdbcUrl,
                ClickHouseContainer.CLICKHOUSE_USER,
                "PASS=" + ClickHouseContainer.CLICKHOUSE_PASSWORD);
        pxfVerify.setHost(pxfHost);
        pxfVerify.setPort(pxfPort);
        if (enableProtocolCompression) {
            pxfVerify.addUserParameter(PROTOCOL_COMPRESS_ENABLED);
            pxfVerify.addUserParameter(PROTOCOL_COMPRESS_ALGORITHM_LZ4);
        }
        if (enableHttpConnectionProvider) {
            pxfVerify.addUserParameter(HTTP_CONNECTION_PROVIDER_APACHE);
        }
        cloudberry.createTableAndVerify(pxfVerify);

        try {
            regress.runSqlTest("features/jdbc/clickhouse/write_types");
        } finally {
            cloudberry.dropTable(pxfVerify, true);
            cloudberry.dropTable(pxfWrite, true);
        }
    }

    private void createAndSeedClickhouseReadTable(String jdbcUrl) throws SQLException {
        try (Connection chConn = openClickhouseConnection(jdbcUrl)) {
            createClickhouseServerTable(chConn, CLICKHOUSE_TABLE_READ);
            insertClickhouseReadFixture(chConn);
        }
    }

    private void createClickhouseWriteTable(String externalJdbcUrl) throws SQLException {
        try (Connection chConn = openClickhouseConnection(externalJdbcUrl)) {
            createClickhouseServerTable(chConn, CLICKHOUSE_TABLE_WRITE);
        }
    }

    /** Creates ClickHouse MergeTree table ({@code DROP IF EXISTS} + {@code CREATE}). */
    private void createClickhouseServerTable(Connection chConn, String tableName) throws SQLException {
        try (Statement st = chConn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + tableName);
            st.execute("CREATE TABLE " + tableName + " ( "
                    + "i_int Int32, "
                    + "s_small Int16, "
                    + "b_big Int64, "
                    + "f_float32 Float32, "
                    + "d_float64 Float64, "
                    + "b_bool Bool, "
                    + "dec Decimal(38,10), "
                    + "t_text String, "
                    + "bin String, "                 // binary data
                    + "d_date Date, "
                    + "d_ts DateTime64(3,'UTC'), "
                    + "d_tstz DateTime64(3,'UTC'), "
                    + "d_uuid UUID "
                    + ") ENGINE = MergeTree ORDER BY (i_int)");
        }
    }

    /** Inserts fixture row into {@link #CLICKHOUSE_TABLE_READ} for the read test. */
    private void insertClickhouseReadFixture(Connection chConn) throws SQLException {
        String insertSql = "INSERT INTO " + CLICKHOUSE_TABLE_READ + " ("
                + "i_int, s_small, b_big, f_float32, d_float64, b_bool, dec, t_text, bin, d_date, d_ts, d_tstz, d_uuid"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = chConn.prepareStatement(insertSql)) {
            ps.setInt(1, V_I_INT);
            ps.setShort(2, V_S_SMALL);
            ps.setLong(3, V_B_BIG);
            ps.setFloat(4, V_F_FLOAT32);
            ps.setDouble(5, V_D_FLOAT64);
            ps.setBoolean(6, V_B_BOOL);
            ps.setBigDecimal(7, new BigDecimal(V_DEC_TEXT));
            ps.setString(8, V_T_TEXT);
            ps.setBytes(9, V_BIN_BYTES);
            ps.setDate(10, Date.valueOf(V_D_DATE));
            ps.setTimestamp(11, Timestamp.valueOf(V_D_TS));
            ps.setTimestamp(12, Timestamp.valueOf(V_D_TS));
            ps.setObject(13, UUID.fromString(V_D_UUID));
            ps.executeUpdate();
        }
    }

    private Connection openClickhouseConnection(String jdbcUrl) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", ClickHouseContainer.CLICKHOUSE_USER);
        props.setProperty("password", ClickHouseContainer.CLICKHOUSE_PASSWORD);
        return DriverManager.getConnection(jdbcUrl, props);
    }

}
