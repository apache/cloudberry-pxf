package org.apache.cloudberry.pxf.automation.proxy;

import org.apache.cloudberry.pxf.automation.BaseFunctionality;

/** Base class for proxy tests. */
public abstract class BaseProxy extends BaseFunctionality {
    // Method to be overridden by all extending classes
    protected abstract void prepareData() throws Exception;

    protected abstract void createTables() throws Exception;

    protected abstract void queryResults() throws Exception;

    /**
     * Runs the proxy test stages in order.
     *
     * @throws Exception
     */
    public void runTest() throws Exception {
        prepareData();
        createTables();
        queryResults();
    }
}
