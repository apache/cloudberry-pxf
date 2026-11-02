package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.DataFile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Slf4j
@RequiredArgsConstructor
public class WriteSynchronizer {

    public interface FilesToCommitProvider {
        Collection<DataFile> get() throws Exception;
    }

    public interface WriteAborter {
        void abort() throws Exception;
    }

    /**
     * The files written by all the segments, handed to the segment that completes last.
     * If any segment failed, none of them must be committed.
     */
    public record Batch(Collection<DataFile> files, boolean failed) {
    }

    private final String transactionId;
    private final Semaphore semaphore = new Semaphore(Integer.MAX_VALUE);
    private final ConcurrentMap<Integer, Collection<DataFile>> files = new ConcurrentHashMap<>();
    private volatile boolean failed;

    public boolean isInUse() {
        return getNumberOfActiveThreads() > 0;
    }

    private int getNumberOfActiveThreads() {
        return Integer.MAX_VALUE - semaphore.availablePermits();
    }

    public boolean open(int segmentId) {
        log.info("Open transaction id {}, segment id {}, current number of active threads is {}",
                transactionId, segmentId, getNumberOfActiveThreads());
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            log.error("Error during write opening", e);
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    /**
     * Saves the files of a segment that completed its write.
     *
     * @return the files of all the segments if this segment is the last one
     */
    public Optional<Batch> saveAndGetBatchIfCompleted(int segmentId, FilesToCommitProvider filesProvider) throws Exception {
        log.info("Complete transaction id {}, segment id {}, current number of active threads is {}",
                transactionId, segmentId, getNumberOfActiveThreads());
        try {
            files.put(segmentId, filesProvider.get());
        } catch (Exception e) {
            failed = true;
            throw e;
        } finally {
            // release the permit even if the segment failed, otherwise the other segments never complete
            semaphore.release();
        }
        return tryToCompleteEverything(segmentId);
    }

    /**
     * Marks the write as failed after an error or a cancel of a segment: the files of the other
     * segments are not committed either, as the statement fails as a whole.
     *
     * @return the files of all the segments if this segment is the last one
     */
    public Optional<Batch> abortAndGetBatchIfCompleted(int segmentId, WriteAborter aborter) throws Exception {
        log.info("Abort transaction id {}, segment id {}, current number of active threads is {}",
                transactionId, segmentId, getNumberOfActiveThreads());
        failed = true;
        try {
            aborter.abort();
        } finally {
            semaphore.release();
        }
        return tryToCompleteEverything(segmentId);
    }

    private Optional<Batch> tryToCompleteEverything(int segmentId) {
        //check if the current segment is last
        if(!semaphore.tryAcquire(Integer.MAX_VALUE)) {
            return Optional.empty();
        }
        log.info("Attempt to complete transaction id {}, segment id {} on node", transactionId, segmentId);
        try{
            var batch = new Batch(files.values().stream().flatMap(Collection::stream).toList(), failed);
            files.clear();
            failed = false;
            return Optional.of(batch);
        } finally {
            semaphore.release(Integer.MAX_VALUE);
        }
    }

}
