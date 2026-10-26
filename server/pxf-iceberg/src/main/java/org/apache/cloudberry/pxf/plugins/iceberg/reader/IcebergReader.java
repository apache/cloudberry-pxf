package org.apache.cloudberry.pxf.plugins.iceberg.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.data.*;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.util.PartitionUtil;
import org.apache.cloudberry.pxf.api.OneRow;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
@Slf4j
public class IcebergReader implements Closeable {

    protected final TableScan tableScan;
    protected final FileScanTask task;
    private final AtomicLong counter = new AtomicLong(0);
    private CloseableIterable<Record> records;
    private CloseableIterator<Record> recordsToRead;

    public boolean open() {
        var taskRecords = task.file().recordCount();
        if(taskRecords == 0){
            return false;
        }
        this.records = readRecords();
        this.recordsToRead = records.iterator();
        return true;
    }

    @Override
    public void close() throws IOException {
        log.info("Reading of {} records from {} has been finished", counter.get(), task.file().location());
        try {
            if(recordsToRead != null){
                recordsToRead.close();
            }
        } finally {
            // closing the iterator alone leaves the file reader open (e.g. the ORC Reader)
            if(records != null){
                records.close();
            }
        }
    }

    public OneRow next() {
        if(recordsToRead == null || !recordsToRead.hasNext()){
            return null;
        }
        counter.incrementAndGet();
        return new OneRow(recordsToRead.next());
    }

    private CloseableIterable<Record> readRecords() {
        var io = tableScan.table().io();
        DeleteFilter<Record> deletes = new GenericDeleteFilter(io, task, tableScan.table().schema(), tableScan.schema());
        Schema readSchema = deletes.requiredSchema();
        CloseableIterable<Record> records = readFile(
                io.newInputFile(task.file()),
                readSchema,
                PartitionUtil.constantsMap(task, IdentityPartitionConverters::convertConstant)
        );
        records = deletes.filter(records);
        return applyResidual(records, readSchema, task.residual());
    }

    private CloseableIterable<Record> applyResidual(CloseableIterable<Record> records, Schema recordSchema, Expression residual) {
        if (residual == null || residual == Expressions.alwaysTrue()) {
            return records;
        }
        InternalRecordWrapper wrapper = new InternalRecordWrapper(recordSchema.asStruct());
        Evaluator filter = new Evaluator(recordSchema.asStruct(), residual, tableScan.isCaseSensitive());
        return CloseableIterable.filter(records, (record) -> filter.eval(wrapper.wrap(record)));
    }

    protected CloseableIterable<Record> readFile(InputFile input, Schema fileProjection, Map<Integer, ?> partition) {
        throw new UnsupportedOperationException(
                String.format("Cannot read %s file: %s",
                        task.file().format().name(),
                        task.file().location()
                )
        );
    }

}
