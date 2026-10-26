package org.apache.cloudberry.pxf.plugins.iceberg.catalog;

// the PXF server is part of the key: servers may use the same catalog name and type
// with a different configuration (metastore, credentials), and must not share a catalog
public record CatalogKey(String serverName, String name, CatalogType type) {
}
