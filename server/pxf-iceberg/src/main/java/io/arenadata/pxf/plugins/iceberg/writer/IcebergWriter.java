package io.arenadata.pxf.plugins.iceberg.writer;

import org.apache.iceberg.data.Record;

import java.io.IOException;
import java.util.Collection;

public interface IcebergWriter {

    void write(Record record) throws IOException;

    Collection<FileToCommit> completeAndGetFilesToCommit() throws Exception;

}
