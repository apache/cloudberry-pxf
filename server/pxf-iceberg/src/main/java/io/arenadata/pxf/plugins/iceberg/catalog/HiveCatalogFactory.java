package io.arenadata.pxf.plugins.iceberg.catalog;

import io.arenadata.pxf.plugins.iceberg.IcebergSettings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.hive.HiveCatalog;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class HiveCatalogFactory implements CatalogFactory {

    private static final String CACHE_TOKEN_CONFIG_NAME = "cache.token",
                                CLIENT_POOL_CACHE_KEYS_PROPERTY_VALUE = "conf:" + CACHE_TOKEN_CONFIG_NAME;

    @Override
    public CatalogType getType() {
        return CatalogType.HIVE_METASTORE;
    }

    @Override
    public Catalog create(IcebergSettings settings) {
        var catalog = new HiveCatalog();
        catalog.setConf(getConfiguration(settings));
        catalog.initialize(settings.getCatalogName(), getCatalogProperties(settings));
        return catalog;
    }

    private Map<String, String> getCatalogProperties(IcebergSettings settings) {
        HashMap<String, String> props = new HashMap<>(settings.getCatalogProperties());
        props.put(CatalogProperties.CLIENT_POOL_CACHE_KEYS, CLIENT_POOL_CACHE_KEYS_PROPERTY_VALUE);
        return props;
    }

    private Configuration getConfiguration(IcebergSettings settings) {
        Configuration conf = new Configuration(settings.getConfiguration());
        if(!settings.isSsl()) {
            return conf;
        }
        conf.set("metastore.use.SSL", "true");
        conf.set("metastore.truststore.path", getSSLTruststorePath(settings));
        conf.set("metastore.truststore.password", ObjectUtils.firstNonNull(settings.getSslTrustStorePassword(), JVM_TRUSTSTORE_DEFAULT_PASSWORD));
        conf.set(CACHE_TOKEN_CONFIG_NAME, Integer.toString(settings.hashCode()));
        return conf;
    }

    private String getSSLTruststorePath(IcebergSettings settings) {
        if(StringUtils.isBlank(settings.getSslTrustStorePath())) {
            return System.getProperty("java.home") + JVM_TRUSTSTORE_PATH ;
        }
        return settings.getSslTrustStorePath();
    }
}
