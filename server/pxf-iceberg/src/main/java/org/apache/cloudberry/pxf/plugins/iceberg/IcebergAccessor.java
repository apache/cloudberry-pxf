package org.apache.cloudberry.pxf.plugins.iceberg;

import org.apache.cloudberry.pxf.plugins.iceberg.reader.*;
import org.apache.cloudberry.pxf.plugins.iceberg.catalog.CatalogProvider;
import org.apache.cloudberry.pxf.plugins.iceberg.table.TableWrapper;
import org.apache.cloudberry.pxf.plugins.iceberg.writer.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.data.Record;
import org.apache.cloudberry.pxf.api.OneRow;
import org.apache.cloudberry.pxf.api.model.Accessor;
import org.apache.cloudberry.pxf.api.model.BasePlugin;
import org.apache.cloudberry.pxf.api.utilities.SpringContext;
import org.springframework.util.CollectionUtils;

@Slf4j
public class IcebergAccessor extends BasePlugin implements Accessor {

    private final CatalogProvider catalogProvider;
    private final ReaderProvider readerProvider;
    private final WriterFactory writerFactory;
    private TableWrapper table;
    private IcebergSettings settings;
    private IcebergReader reader;
    private IcebergWriter writer;

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
    public synchronized void closeForWrite() {
        // may be called twice (pxf_cancel_backend and the write service) or after a failed openForWrite
        if(writer == null) {
            return;
        }
        log.info("Closing writing to {}, segment is {}, transaction id is {}",
                context.getDataSource(), context.getSegmentId(), context.getTransactionId());
        IcebergWriter writerToClose = writer;
        writer = null;
        try {
            var result = writerToClose.completeAndGetFilesToCommit();
            if(CollectionUtils.isEmpty(result)) {
                return;
            }
            table.appendFiles(settings.getBranchName(), result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
