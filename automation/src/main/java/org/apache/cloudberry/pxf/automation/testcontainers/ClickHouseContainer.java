package org.apache.cloudberry.pxf.automation.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers wrapper around ClickHouse server.
 *
 * The container joins a shared Docker network with alias {@code clickhouse},
 * so PXF inside the Cloudberry container can reach it at {@code clickhouse:8123} (HTTP)
 * or {@code clickhouse:9000} (native protocol).
 */
public class ClickHouseContainer extends GenericContainer<ClickHouseContainer> {

    private static final String DEFAULT_IMAGE = "clickhouse/clickhouse-server";
    public static final int HTTP_PORT = 8123;
    public static final int NATIVE_PORT = 9000;
    public static final String NETWORK_ALIAS = "clickhouse";

    public ClickHouseContainer(String tag, Network network) {
        super(DockerImageName.parse(DEFAULT_IMAGE + ":" + tag));
        super.withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS)
            .withExposedPorts(HTTP_PORT, NATIVE_PORT)
            .waitingFor(Wait.forHttp("/ping").forPort(HTTP_PORT));
    }

    /** JDBC URL reachable from the host (uses the mapped HTTP port). */
    public String getJdbcUrl() {
        return "jdbc:clickhouse://localhost:" + getMappedPort(HTTP_PORT) + "/default";
    }

    /** JDBC URL reachable from other containers on the shared Docker network. */
    public String getInternalJdbcUrl() {
        return "jdbc:clickhouse://" + NETWORK_ALIAS + ":" + HTTP_PORT + "/default";
    }

    public int getHttpMappedPort() {
        return getMappedPort(HTTP_PORT);
    }

    public int getNativeMappedPort() {
        return getMappedPort(NATIVE_PORT);
    }
}
