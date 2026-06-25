package io.arenadata.pxf.plugins.iceberg.catalog;

import io.arenadata.pxf.plugins.iceberg.IcebergSettings;
import io.arenadata.pxf.plugins.iceberg.table.TableWrapper;
import io.github.resilience4j.retry.Retry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;

@Slf4j
@RequiredArgsConstructor
public class CatalogWrapper implements AutoCloseable {

    private final CatalogKey catalogKey;
    @Getter
    private final IcebergSettings settings;
    private final Catalog catalog;
    private final Retry retry;

    public TableWrapper loadTable(String dataSource) {
        var tableIdentifier = TableIdentifier.of(dataSource.split("\\."));
        return new TableWrapper(retry, catalog.loadTable(tableIdentifier));
    }

    @Override
    public void close() {
        try{
            if(catalog instanceof AutoCloseable c) {
                c.close();
            }
        } catch(Exception e) {
            log.error("Error during closing catalog {} with type {} ", catalogKey.name(), catalogKey.type(), e);
        }
    }

}
