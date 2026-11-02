package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.BaseTaskWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

@RequiredArgsConstructor
public class IcebergWriterImpl implements IcebergWriter {

    private final BaseTaskWriter<Record> writer;

    @Override
    public void write(Record record) throws IOException {
        writer.write(record);
    }

    @Override
    public Collection<DataFile> completeAndGetFilesToCommit() throws IOException {
        try{
            // the writer's own data files keep the column metrics and the file format
            return Arrays.asList(writer.dataFiles());
        } finally {
            writer.close();
        }
    }

    @Override
    public void abort() throws IOException {
        writer.abort();
    }

}
