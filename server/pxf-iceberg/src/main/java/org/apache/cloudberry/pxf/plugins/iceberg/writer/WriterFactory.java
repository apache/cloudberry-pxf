package org.apache.cloudberry.pxf.plugins.iceberg.writer;

import org.apache.cloudberry.pxf.plugins.iceberg.IcebergSettings;
import org.apache.cloudberry.pxf.plugins.iceberg.table.TableWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.InternalRecordWrapper;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.BaseTaskWriter;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitionedFanoutWriter;
import org.apache.iceberg.io.UnpartitionedWriter;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WriterFactory {

    private final Map<String, WriteSynchronizer> synchronizers = new ConcurrentHashMap<>();

    public IcebergWriter create(TableWrapper table, RequestContext context, IcebergSettings settings) {
        var writer = new IcebergWriterImpl(createWriter(
                table.getTable(),
                context.getSegmentId(),
                context.getGpSessionId()
        ));
        return addSynchronizationIfNeeded(writer, table.getTable(), context);
    }

    private IcebergWriter addSynchronizationIfNeeded(IcebergWriter writer, Table table, RequestContext context) {
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
                () -> cleanSynchronizer(context.getTransactionId()),
                files -> files.forEach(file -> table.io().deleteFile(file.location()))
        );

    }

    private BaseTaskWriter<Record> createWriter(Table table, int partitionId, int taskId) {
        // follow the write properties of the table: file format, file size, compression, metrics
        Map<String, String> properties = table.properties();
        FileFormat fileFormat = FileFormat.fromString(PropertyUtil.propertyAsString(properties,
                TableProperties.DEFAULT_FILE_FORMAT, TableProperties.DEFAULT_FILE_FORMAT_DEFAULT));
        if (fileFormat == FileFormat.ORC) {
            // Iceberg's ORC writer needs the "nohive" flavor of orc-core, while PXF ships the regular one.
            // Iceberg keeps the format of every data file, so Parquet files in an ORC table stay readable.
            log.warn("Table {} has {}=orc, writing Parquet files instead: PXF cannot write ORC files to Iceberg",
                    table.name(), TableProperties.DEFAULT_FILE_FORMAT);
            fileFormat = FileFormat.PARQUET;
        }
        long targetFileSizeInBytes = PropertyUtil.propertyAsLong(properties,
                TableProperties.WRITE_TARGET_FILE_SIZE_BYTES, TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT);
        GenericAppenderFactory appenderFactory = new GenericAppenderFactory(table.schema(), table.spec());
        appenderFactory.setAll(properties);

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
                    targetFileSizeInBytes);
        }
        PartitionKey partitionKey = new PartitionKey(table.spec(), table.spec().schema());
        return new PartitionedFanoutWriter<>(table.spec(),
                fileFormat,
                appenderFactory,
                outputFileFactory,
                table.io(),
                targetFileSizeInBytes) {
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
