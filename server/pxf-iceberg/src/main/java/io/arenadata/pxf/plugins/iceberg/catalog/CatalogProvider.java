package io.arenadata.pxf.plugins.iceberg.catalog;

import io.arenadata.pxf.plugins.iceberg.IcebergSettings;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Component
public class CatalogProvider implements AutoCloseable {

    private final Retry retry;
    private final Map<CatalogType, CatalogFactory> factories;
    private final ConcurrentMap<CatalogKey, CatalogWrapper> catalogs = new ConcurrentHashMap<>();

    public CatalogProvider(Collection<CatalogFactory> factories, Retry retry) {
        this.retry = retry;
        this.factories = factories.stream().collect(toMap(CatalogFactory::getType, Function.identity()));
    }

    @Override
    public void close() {
        catalogs.values().forEach(CatalogWrapper::close);
    }

    public CatalogWrapper get(IcebergSettings settings) {
        var catalogKey = new CatalogKey(settings.getCatalogName(), settings.getCatalogType());
        AtomicReference<CatalogWrapper> toClose = new AtomicReference<>();
        var result = catalogs.compute(catalogKey, (key, current) -> {
            if (current != null && Objects.equals(current.getSettings(), settings)) {
                return current;
            }
            toClose.set(current);
            return createCatalog(key, settings);
        });
        if (toClose.get() != null) {
            toClose.get().close();
        }
        return result;
    }

    private CatalogWrapper createCatalog(CatalogKey catalogKey, IcebergSettings settings) {
        return Optional.ofNullable(factories.get(catalogKey.type()))
                .map(factory -> factory.create(settings))
                .map(catalog -> new CatalogWrapper(catalogKey, settings, catalog, retry))
                .orElseThrow(() -> new UnsupportedOperationException("Catalog with type " + catalogKey.type() + " is not supported"));
    }

}
