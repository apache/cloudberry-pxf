package org.apache.cloudberry.pxf.plugins.iceberg;

import org.apache.cloudberry.pxf.plugins.iceberg.catalog.CatalogType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.hadoop.conf.Configuration;
import org.apache.cloudberry.pxf.api.model.RequestContext;

import java.util.Map;
import java.util.Objects;

import static org.apache.iceberg.SnapshotRef.MAIN_BRANCH;

@Data
public class IcebergSettings {

    public static final String ICEBERG_CONFIG_PREFIX = "iceberg.config.",
            ICEBERG_PARAM_PREFIX = "iceberg.param.",
            ICEBERG_CATALOG_TYPE = "catalog.type",
            ICEBERG_CATALOG_NAME = "catalog.name",
            SSL_ENABLED = "ssl.enabled",
            SSL_TRUSTSTORE_PATH = "ssl.truststore.path",
            SSL_TRUSTSTORE_PASSWORD = "ssl.truststore.password",
            REF_OPTION_NAME = "ref",
            FRAGMENT_SIZE_OPTION_NAME = "fragmentSize";

    private final String catalogName;
    private final CatalogType catalogType;
    private final Map<String, String> catalogProperties;
    private final String branchName;
    private final Long fragmentSize;
    private final boolean ssl;
    private final String sslTrustStorePath;
    private final String sslTrustStorePassword;
    // excluded from equals/hashCode: Configuration doesn't override equals(),
    // so including it would make CatalogProvider treat every request as a cache miss
    @EqualsAndHashCode.Exclude
    private final Configuration configuration;


    /**
     * Whether a catalog created for the other settings can be reused for these ones. Per-query options
     * (branch, fragment size) do not matter, so concurrent queries using different branches share a catalog.
     */
    public boolean hasSameCatalogConfig(IcebergSettings other) {
        return other != null
                && Objects.equals(catalogName, other.catalogName)
                && catalogType == other.catalogType
                && Objects.equals(catalogProperties, other.catalogProperties)
                && ssl == other.ssl
                && Objects.equals(sslTrustStorePath, other.sslTrustStorePath)
                && Objects.equals(sslTrustStorePassword, other.sslTrustStorePassword);
    }

    public static IcebergSettings create(Configuration configuration, RequestContext context) {
        Map<String, String> configs = configuration.getPropsWithPrefix(ICEBERG_CONFIG_PREFIX);
        return new IcebergSettings(
            configs.get(ICEBERG_CATALOG_NAME),
            CatalogType.findByName(configs.get(ICEBERG_CATALOG_TYPE)),
            configuration.getPropsWithPrefix(ICEBERG_PARAM_PREFIX),
            context.getOption(REF_OPTION_NAME, MAIN_BRANCH),
            context.getOption(FRAGMENT_SIZE_OPTION_NAME, 0L),
            Boolean.parseBoolean(configs.get(SSL_ENABLED)),
            configs.get(SSL_TRUSTSTORE_PATH),
            configs.get(SSL_TRUSTSTORE_PASSWORD),
            configuration
        );
    }

}
