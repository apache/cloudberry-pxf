package io.arenadata.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import org.apache.iceberg.data.Record;

import java.io.IOException;
import java.util.Collection;

@RequiredArgsConstructor
public class IcebergWriterWithSynchronization implements IcebergWriter {

    private final int segmentId;
    private final IcebergWriter delegate;
    private final WriteSynchronizer synchronizer;
    private final Runnable synchronizerCleaner;

    @Override
    public void write(Record record) throws IOException {
        delegate.write(record);
    }

    @Override
    public Collection<FileToCommit> completeAndGetFilesToCommit() throws Exception {
        try{
            return synchronizer.saveAndGetFullListIfCompleted(segmentId, delegate::completeAndGetFilesToCommit);
        } finally {
            synchronizerCleaner.run();
        }
    }

}
