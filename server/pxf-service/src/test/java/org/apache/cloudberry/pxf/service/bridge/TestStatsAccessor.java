package org.apache.cloudberry.pxf.service.bridge;

import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.StatsAccessor;

public class TestStatsAccessor extends TestAccessor implements StatsAccessor {
    @Override
    public void retrieveStats() {
    }

    @Override
    public OneRow emitAggObject() {
        return null;
    }
}
