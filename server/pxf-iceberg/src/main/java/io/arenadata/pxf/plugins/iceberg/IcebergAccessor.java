package io.arenadata.pxf.plugins.iceberg;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.arenadata.pxf.plugins.iceberg.reader.*;
import io.arenadata.pxf.plugins.iceberg.catalog.CatalogProvider;
import io.arenadata.pxf.plugins.iceberg.table.TableWrapper;
import io.arenadata.pxf.plugins.iceberg.writer.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.data.Record;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.model.Accessor;
import org.greenplum.pxf.api.model.BasePlugin;
import org.greenplum.pxf.api.model.ProtocolVersionV1Aware;
import org.greenplum.pxf.api.utilities.SpringContext;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Slf4j
public class IcebergAccessor extends BasePlugin implements Accessor, ProtocolVersionV1Aware {

    private final CatalogProvider catalogProvider;
    private final ReaderProvider readerProvider;
    private final WriterFactory writerFactory;
    private TableWrapper table;
    private IcebergSettings settings;
    private IcebergReader reader;
    private IcebergWriter writer;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public IcebergAccessor() {
        this(SpringContext.getBean(CatalogProvider.class),
            SpringContext.getBean(ReaderProvider.class),
            SpringContext.getBean(WriterFactory.class));
    }

    public IcebergAccessor(CatalogProvider catalogProvider,
                           ReaderProvider readerProvider,
                           WriterFactory writerFactory) {
        this.catalogProvider = catalogProvider;
        this.readerProvider = readerProvider;
        this.writerFactory = writerFactory;
    }

    @Override
    public void afterPropertiesSet() {
        this.settings = IcebergSettings.create(configuration, context);
        this.table = catalogProvider.get(settings).loadTable(context.getDataSource());
    }

    @Override
    public boolean openForRead() {
        context.setMetadata(table.schema());
        this.reader = readerProvider.get(context.getFragmentMetadata());
        return reader.open();
    }

    @Override
    public OneRow readNextObject() {
        if(reader == null){
            return null;
        }
        return reader.next();
    }

    @Override
    public void closeForRead() throws Exception {
        if(reader != null) {
            reader.close();
        }
    }

    @Override
    public boolean openForWrite() throws Exception {
        log.info("Opening for write to {}, segment is {}, transaction id is {}",
                context.getDataSource(), context.getSegmentId(), context.getTransactionId());
        context.setMetadata(table.schema());
        this.writer = writerFactory.create(table, context, settings);
        return writer != null;
    }

    @Override
    public boolean writeNextObject(OneRow row) throws Exception {
        writer.write((Record) row.getData());
        return true;
    }

    @Override
    public void closeForWrite() {
        log.info("Closing writing to {}, segment is {}, transaction id is {}",
                context.getDataSource(), context.getSegmentId(), context.getTransactionId());
        try {
            var result = writer.completeAndGetFilesToCommit();
            if(CollectionUtils.isEmpty(result)) {
                return;
            }
            table.appendFiles(settings.getBranchName(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] closeForWriteAndReturnMetadata() throws Exception {
        var result = new WriteResult(context.getSegmentId(), writer.completeAndGetFilesToCommit());
        return objectMapper.writeValueAsBytes(result);
    }

    @Override
    public void commit(List<byte[]> fullMetadata) {
        var files = fullMetadata.stream()
                .map(metadata -> {
                    try {
                        return objectMapper.readValue(metadata, WriteResult.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(result -> result.files.stream())
                .toList();
        table.appendFiles(settings.getBranchName(), files);
    }

    record WriteResult(int segmentId, Collection<FileToCommit> files) {}

}
