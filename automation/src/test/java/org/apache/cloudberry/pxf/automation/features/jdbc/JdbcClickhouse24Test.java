package org.apache.cloudberry.pxf.automation.features.jdbc;

import org.apache.cloudberry.pxf.automation.AbstractTestcontainersTest;
import org.apache.cloudberry.pxf.automation.testcontainers.ClickHouseContainer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JdbcClickhouse24Test extends AbstractTestcontainersTest {

    private ClickHouseContainer clickhouseContainer;

    @Override
    public void beforeClass() throws Exception {
        clickhouseContainer = new ClickHouseContainer("24", container.getSharedNetwork());
        clickhouseContainer.start();
    }

    @Override
    public void afterClass() throws Exception {
        if (clickhouseContainer != null) {
            clickhouseContainer.stop();
        }
    }

    @Test(groups = {"testcontainers", "jdbc-tc"})
    public void containersAreRunning() {
        Assert.assertTrue(container.isRunning(), "PXFCloudberry container should be running");
        Assert.assertTrue(clickhouseContainer.isRunning(), "ClickHouse container should be running");
    }
}
