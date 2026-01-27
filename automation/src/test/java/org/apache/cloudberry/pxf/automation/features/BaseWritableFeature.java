package org.apache.cloudberry.pxf.automation.features;

import org.apache.cloudberry.pxf.automation.structures.tables.pxf.ReadableExternalTable;
import org.apache.cloudberry.pxf.automation.structures.tables.pxf.WritableExternalTable;

public class BaseWritableFeature extends BaseFeature {

    protected WritableExternalTable writableExTable;
    protected ReadableExternalTable readableExTable;
    // path in hdfs for writable output
    protected String hdfsWritePath;
    protected String writableTableName = "writable_table";
    protected String readableTableName = "readable_table";

    /**
     * Set writable directory
     */
    @Override
    protected void beforeClass() throws Exception {
        super.beforeClass();
        if (hdfs != null) {
            hdfsWritePath = hdfs.getWorkingDirectory() + "/writable_results/";
        }
    }

    @Override
    protected void beforeMethod() throws Exception {
        super.beforeMethod();
        // Ensure writable target directory exists before each test when data is preserved.
        if (hdfs != null && hdfsWritePath != null && !hdfs.doesFileExist(hdfsWritePath)) {
            hdfs.createDirectory(hdfsWritePath);
        }
    }

    /**
     *  clean writable directory
     */
    @Override
    protected void afterMethod() throws Exception {
        super.afterMethod();
        // When PXF_TEST_KEEP_DATA=true we keep files for subsequent validations.
        if ("true".equalsIgnoreCase(org.apache.cloudberry.pxf.automation.utils.system.ProtocolUtils.getPxfTestKeepData())) {
            return;
        }
        if (hdfs != null && hdfsWritePath != null) {
            hdfs.removeDirectory(hdfsWritePath);
        }
    }
}
