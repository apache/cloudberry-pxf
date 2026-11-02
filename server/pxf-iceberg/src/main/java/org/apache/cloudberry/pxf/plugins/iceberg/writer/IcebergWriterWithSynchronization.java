package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.data.Record;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class IcebergWriterWithSynchronization implements IcebergWriter {

    private final int segmentId;
    private final IcebergWriter delegate;
    private final WriteSynchronizer synchronizer;
    private final Runnable synchronizerCleaner;
    private final Consumer<Collection<DataFile>> filesDeleter;

    @Override
    public void write(Record record) throws IOException {
        delegate.write(record);
    }

    @Override
    public Collection<DataFile> completeAndGetFilesToCommit() throws Exception {
        try{
            return filesToCommit(synchronizer.saveAndGetBatchIfCompleted(segmentId, delegate::completeAndGetFilesToCommit));
        } finally {
            synchronizerCleaner.run();
        }
    }

    @Override
    public void abort() throws Exception {
        try{
            filesToCommit(synchronizer.abortAndGetBatchIfCompleted(segmentId, delegate::abort));
        } finally {
            synchronizerCleaner.run();
        }
    }

    private Collection<DataFile> filesToCommit(Optional<WriteSynchronizer.Batch> batch) {
        if(batch.isEmpty()) {
            return List.of();
        }
        if(batch.get().failed()) {
            log.warn("Write of a segment failed, deleting {} files written by the segments on this host",
                    batch.get().files().size());
            filesDeleter.accept(batch.get().files());
            return List.of();
        }
        return batch.get().files();
    }

}
