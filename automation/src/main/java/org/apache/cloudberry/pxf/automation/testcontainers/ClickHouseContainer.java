package org.apache.cloudberry.pxf.automation.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers wrapper around ClickHouse server.
 *
 * The container joins a shared Docker network with alias `clickhouse`,
 * so PXF inside the Cloudberry container can reach it at `clickhouse:8123` (HTTP).
 *
 */
public class ClickHouseContainer extends GenericContainer<ClickHouseContainer> {

    private static final String DEFAULT_IMAGE = "clickhouse/clickhouse-server";
    public static final int HTTP_PORT = 8123;
    public static final String NETWORK_ALIAS = "clickhouse";

    /**
     * Credentials for the test container. ClickHouse 24+ restricts network access for `default`
     * until a password is set; `CLICKHOUSE_PASSWORD` configures the server and JDBC must match.
     */
    public static final String CLICKHOUSE_USER = "default";
    public static final String CLICKHOUSE_PASSWORD = "pxf-test";

    public ClickHouseContainer(String tag, Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE + ":" + tag));
        super.withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS)
            .withExposedPorts(HTTP_PORT)
            .withEnv("CLICKHOUSE_USER", CLICKHOUSE_USER)
            .withEnv("CLICKHOUSE_PASSWORD", CLICKHOUSE_PASSWORD)
            .waitingFor(Wait.forHttp("/ping").forPort(HTTP_PORT));
    }

    /** JDBC URL over HTTP, reachable from the host (mapped `HTTP_PORT`). */
    public String getJdbcUrl() {
        return "jdbc:clickhouse://localhost:" + getMappedPort(HTTP_PORT) + "/default";
    }

    /** JDBC URL over HTTP for PXF / other containers. */
    public String getInternalJdbcUrl() {
        return "jdbc:clickhouse://" + NETWORK_ALIAS + ":" + HTTP_PORT + "/default";
    }

    public int getHttpMappedPort() {
        return getMappedPort(HTTP_PORT);
    }
}
