package io.arenadata.pxf.plugins.iceberg.writer;

import io.arenadata.pxf.plugins.iceberg.IcebergSettings;
import io.arenadata.pxf.plugins.iceberg.table.TableWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.InternalRecordWrapper;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.BaseTaskWriter;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitionedFanoutWriter;
import org.apache.iceberg.io.UnpartitionedWriter;
import org.greenplum.pxf.api.model.ProtocolVersion;
import org.greenplum.pxf.api.model.RequestContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WriterFactory {

    private static final long TARGET_FILE_SIZE_IN_BYTES = 256 * 1024 * 1024;

    private final Map<String, WriteSynchronizer> synchronizers = new ConcurrentHashMap<>();

    public IcebergWriter create(TableWrapper table, RequestContext context, IcebergSettings settings) {
        var writer = new IcebergWriterImpl(createWriter(
                table.getTable(),
                FileFormat.PARQUET,
                context.getSegmentId(),
                context.getGpSessionId()
        ));
        return addSynchronizationIfNeeded(writer, context);
    }

    private IcebergWriter addSynchronizationIfNeeded(IcebergWriter writer, RequestContext context) {
        if(ProtocolVersion.V1.equals(context.getProtocolVersion())) {
            // we don't need to add synchronization since it will be done by using master-commit protocol
            return writer;
        }
        var synchronizer = synchronizers.compute(context.getTransactionId(), (key, existed) -> {
            var current = existed != null ? existed : new WriteSynchronizer(key);
            if(current.open(context.getSegmentId())) {
                return current;
            }
            return null;
        });
        if(synchronizer == null) {
            return writer;
        }
        return new IcebergWriterWithSynchronization(context.getSegmentId(), writer, synchronizer,
                () -> cleanSynchronizer(context.getTransactionId())
        );

    }

    private BaseTaskWriter<Record> createWriter(Table table, FileFormat fileFormat, int partitionId, int taskId) {
        GenericAppenderFactory appenderFactory = new GenericAppenderFactory(table.schema(), table.spec());

        OutputFileFactory outputFileFactory = OutputFileFactory
                .builderFor(table, partitionId, taskId)
                .format(fileFormat)
                .build();
        if(!table.spec().isPartitioned()) {
            return new UnpartitionedWriter<>(table.spec(),
                    fileFormat,
                    appenderFactory,
                    outputFileFactory,
                    table.io(),
                    TARGET_FILE_SIZE_IN_BYTES);
        }
        PartitionKey partitionKey = new PartitionKey(table.spec(), table.spec().schema());
        return new PartitionedFanoutWriter<>(table.spec(),
                fileFormat,
                appenderFactory,
                outputFileFactory,
                table.io(),
                TARGET_FILE_SIZE_IN_BYTES) {
            @Override
            protected PartitionKey partition(Record record) {
                InternalRecordWrapper wrapper = new InternalRecordWrapper(table.schema().asStruct());
                partitionKey.partition(wrapper.wrap(record));
                return partitionKey;
            }
        };
    }

    private void cleanSynchronizer(String transactionId) {
        synchronizers.computeIfPresent(transactionId,
                (key, synchronizer) -> synchronizer.isInUse() ? synchronizer : null
        );
    }

}
