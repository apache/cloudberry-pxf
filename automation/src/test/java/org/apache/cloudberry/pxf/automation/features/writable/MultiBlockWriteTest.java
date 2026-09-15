package org.apache.cloudberry.pxf.automation.features.writable;

import annotations.WorksWithFDW;
import org.apache.cloudberry.pxf.automation.features.BaseWritableFeature;
import org.apache.cloudberry.pxf.automation.structures.tables.utils.TableFactory;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolEnum;
import org.apache.cloudberry.pxf.automation.utils.system.ProtocolUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Large writes, ported from regression/sql/FDW_MultiBlockDataSmokeTest.sql. */
@WorksWithFDW
public class MultiBlockWriteTest extends BaseWritableFeature {
    private String writePath;

    @Test(groups = {"load", "features"})
    public void writeAndReadMultiBlockData() throws Exception {
        writePath = hdfsWritePath + "/multi_block_write";
        // A retry must not append another 32 million rows to the previous output.
        hdfs.removeDirectory(writePath);

        String[] fields = {"t1 text", "a1 integer"};
        ProtocolEnum protocol = ProtocolUtils.getProtocol();
        String tablePath = protocol.getExternalTablePath(hdfs.getBasePath(), writePath);
        writableExTable = TableFactory.getPxfWritableTextTable("pxf_multi_block_write", fields, tablePath, ",");
        writableExTable.setFormat("CSV");
        writableExTable.setProfile(protocol.value() + ":csv");
        createTable(writableExTable);
        readableExTable = TableFactory.getPxfReadableTextTable("pxf_multi_block_read", fields, tablePath, ",");
        readableExTable.setFormat("CSV");
        readableExTable.setProfile(protocol.value() + ":csv");
        createTable(readableExTable);

        runSqlTest("features/hdfs/write_multi_block");
        verifyFirstRows();
    }

    private void verifyFirstRows() throws Exception {
        // Preserve the original filesystem check without reading whole large files into memory.
        List<String> firstRows = new ArrayList<>();
        for (String file : hdfs.list(writePath)) {
            Path path = new Path(file);
            FileSystem fs = path.getFileSystem(hdfs.getConfiguration());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    fs.open(path), StandardCharsets.UTF_8))) {
                String firstRow = reader.readLine();
                if (firstRow != null) {
                    firstRows.add(firstRow);
                }
            }
        }
        Assert.assertFalse("Insert did not produce any data files", firstRows.isEmpty());
        Collections.sort(firstRows);
        Assert.assertEquals("t1,1", firstRows.get(0));
    }

    @Override
    protected void afterClass() throws Exception {
        if (writableExTable != null) {
            gpdb.dropTable(writableExTable, true);
        }
        if (readableExTable != null) {
            gpdb.dropTable(readableExTable, true);
        }
    }
}
