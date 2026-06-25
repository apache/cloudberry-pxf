package io.arenadata.pxf.plugins.iceberg.table;

import io.arenadata.pxf.plugins.iceberg.writer.FileToCommit;
import io.github.resilience4j.retry.Retry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;

import java.util.Collection;
import java.util.Objects;

import static org.apache.iceberg.SnapshotRef.MAIN_BRANCH;

@RequiredArgsConstructor
@Slf4j
public class TableWrapper {

    private final Retry retry;
    @Getter
    private final Table table;

    public Schema schema() {
        return table.schema();
    }

    public TableScan scan() {
        return table.newScan();
    }

    public void appendFiles(String branchName, Collection<FileToCommit> files) {
        retry.executeRunnable(() -> tryToAppendFiles(branchName,files));
    }

    private void tryToAppendFiles(String branchName, Collection<FileToCommit> files) {
        log.debug("Prepare new append for branch {}", branchName);
        table.refresh();
        var branchToSave = checkOrCreateBranch(branchName);
        table.refresh();
        AppendFiles appendFiles = table.newAppend().toBranch(branchToSave);
        files.stream()
                .peek(file -> log.info("Appending file {} to branch {}", file.path(), branchToSave))
                .map(file -> file.toDataFile(table.spec()))
                .forEach(appendFiles::appendFile);
        log.debug("Apply new append for branch {}", branchToSave);
        appendFiles.apply();
        log.debug("Commit new append for branch {}", branchToSave);
        appendFiles.commit();
    }

    private String checkOrCreateBranch(String branchName) {
        if(StringUtils.isEmpty(branchName)
                || Objects.equals(MAIN_BRANCH, branchName)) {
            return MAIN_BRANCH;
        }
        if(table.refs().containsKey(branchName)){
            return branchName;
        }
        try {
            log.debug("Creating branch {}", branchName);
            table.manageSnapshots()
                    .createBranch(branchName)
                    .commit();
        } catch(Exception e) {
            log.info("Error during branch {} creation: {}", branchName, e.getMessage());
        }
        return branchName;
    }

}
