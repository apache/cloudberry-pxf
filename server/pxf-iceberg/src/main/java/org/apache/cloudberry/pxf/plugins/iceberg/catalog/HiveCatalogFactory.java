package org.apache.cloudberry.pxf.plugins.iceberg.catalog;

import org.apache.cloudberry.pxf.plugins.iceberg.IcebergSettings;
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
        String trustStorePath = getSSLTruststorePath(settings);
        String trustStorePassword = ObjectUtils.firstNonNull(settings.getSslTrustStorePassword(), JVM_TRUSTSTORE_DEFAULT_PASSWORD);
        // Hive 2.3 (shipped with PXF) reads the hive.metastore.* keys, Hive 3+ the metastore.* ones
        for (String prefix : new String[]{"hive.metastore.", "metastore."}) {
            conf.set(prefix + "use.SSL", "true");
            conf.set(prefix + "truststore.path", trustStorePath);
            conf.set(prefix + "truststore.password", trustStorePassword);
        }
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
