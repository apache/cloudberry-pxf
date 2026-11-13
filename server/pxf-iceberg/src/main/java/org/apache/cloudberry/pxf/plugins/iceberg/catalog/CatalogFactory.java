package org.apache.cloudberry.pxf.plugins.iceberg.catalog;

import org.apache.cloudberry.pxf.plugins.iceberg.IcebergSettings;
import org.apache.iceberg.catalog.Catalog;

public interface CatalogFactory {

    String JVM_TRUSTSTORE_PATH = "/lib/security/cacerts",
           JVM_TRUSTSTORE_DEFAULT_PASSWORD = "changeit";

    CatalogType getType();

    Catalog create(IcebergSettings settings);

}
