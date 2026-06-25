package io.arenadata.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Slf4j
@RequiredArgsConstructor
public class WriteSynchronizer {

    public interface FilesToCommitProvider {
        Collection<FileToCommit> get() throws Exception;
    }

    private final String transactionId;
    private final Semaphore semaphore = new Semaphore(Integer.MAX_VALUE);
    private final ConcurrentMap<Integer, Collection<FileToCommit>> files = new ConcurrentHashMap<>();

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
            return false;
        }
        return true;
    }

    public Collection<FileToCommit> saveAndGetFullListIfCompleted(int segmentId, FilesToCommitProvider filesProvider) throws Exception {
        log.info("Complete transaction id {}, segment id {}, current number of active threads is {}",
                transactionId, segmentId, getNumberOfActiveThreads());
        files.put(segmentId, filesProvider.get());
        semaphore.release();
        return tryToCompleteEverything(segmentId);
    }

    private Collection<FileToCommit> tryToCompleteEverything(int segmentId) {
        //check if the current segment is last
        if(!semaphore.tryAcquire(Integer.MAX_VALUE)) {
            return List.of();
        }
        log.info("Attempt to complete transaction id {}, segment id {} on node", transactionId, segmentId);
        try{
            var result = files.values().stream().flatMap(Collection::stream).toList();
            files.clear();
            return result;
        } finally {
            semaphore.release(Integer.MAX_VALUE);
        }
    }

}
