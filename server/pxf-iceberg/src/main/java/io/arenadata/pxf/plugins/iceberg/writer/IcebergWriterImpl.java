package io.arenadata.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
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
    public Collection<FileToCommit> completeAndGetFilesToCommit() throws IOException {
        try{
            return Arrays.stream(writer.dataFiles()).map(FileToCommit::from).toList();
        } finally {
            writer.close();
        }
    }

}
