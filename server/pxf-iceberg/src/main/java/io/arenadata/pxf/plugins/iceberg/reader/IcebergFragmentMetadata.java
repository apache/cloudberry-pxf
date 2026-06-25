package io.arenadata.pxf.plugins.iceberg.reader;

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.TableScan;
import org.greenplum.pxf.api.utilities.FragmentMetadata;

public record IcebergFragmentMetadata(
        TableScan tableScan,
        FileScanTask task        // or just ScanTask
) implements FragmentMetadata {

    public FileFormat getFileFormat() {
        return task.file().format();
    }
}
