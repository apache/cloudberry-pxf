package org.apache.cloudberry.pxf.plugins.iceberg.reader;

import org.apache.iceberg.FileFormat;

public interface ReaderFactory {

    FileFormat getFileFormat();

    IcebergReader create(IcebergFragmentMetadata fragmentMetadata);
}
