package io.arenadata.pxf.plugins.iceberg;

import io.arenadata.pxf.plugins.iceberg.catalog.CatalogType;
import lombok.Data;
import org.apache.hadoop.conf.Configuration;
import org.greenplum.pxf.api.model.RequestContext;

import java.util.Map;

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
            configs.get(SSL_TRUSTSTORE_PASSWORD)
        );
    }

}
