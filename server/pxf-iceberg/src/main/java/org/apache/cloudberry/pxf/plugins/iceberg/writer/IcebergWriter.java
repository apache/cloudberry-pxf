package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.data.Record;

import java.io.IOException;
import java.util.Collection;

public interface IcebergWriter {

    void write(Record record) throws IOException;

    Collection<DataFile> completeAndGetFilesToCommit() throws Exception;

    /**
     * Closes the writer after a failed or cancelled write and deletes the files it has written
     */
    void abort() throws Exception;

}
