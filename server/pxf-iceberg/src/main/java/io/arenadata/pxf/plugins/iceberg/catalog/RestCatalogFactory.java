package io.arenadata.pxf.plugins.iceberg.catalog;

import io.arenadata.pxf.plugins.iceberg.IcebergSettings;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.aws.s3.S3FileIO;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.rest.RESTCatalog;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RestCatalogFactory implements CatalogFactory {

    @Override
    public CatalogType getType() {
        return CatalogType.REST;
    }

    @Override
    public Catalog create(IcebergSettings settings) {
        var catalog = new RESTCatalog();
        catalog.initialize(settings.getCatalogName(), getCatalogProperties(settings));
        return catalog;
    }

    private Map<String, String> getCatalogProperties(IcebergSettings settings) {
        HashMap<String, String> props = new HashMap<>(settings.getCatalogProperties());
        checkFileIoImplProperty(props);
        checkSSLProperties(settings, props);
        return props;
    }

    private void checkFileIoImplProperty(Map<String, String> props) {
        // RESTCatalog defaults to HadoopFileIO which has no s3:// support.
        // Auto-inject S3FileIO when the warehouse is on S3 and io-impl is not set explicitly.
        StorageType.findByWarehouse(props.get(CatalogProperties.WAREHOUSE_LOCATION))
                .filter(StorageType.S3::equals)
                .ifPresent(storageType -> {
                    props.put(CatalogProperties.FILE_IO_IMPL, S3FileIO.class.getName());
                });
    }

    private void checkSSLProperties(IcebergSettings settings, Map<String, String> props) {
        if(!settings.isSsl()
                || StringUtils.isBlank(settings.getSslTrustStorePath())) {
            return;
        }
        // can't use this constant since it's protected
        //props.put(HTTPClient.REST_TLS_CONFIGURER, RestTLSConfigurer.class.getCanonicalName());
        props.put("rest.client.tls.configurer-impl", RestTLSConfigurer.class.getCanonicalName());

        props.put(RestTLSConfigurer.TRUSTSTORE_PATH_PARAM_NAME, settings.getSslTrustStorePath());
        props.put(RestTLSConfigurer.TRUSTSTORE_PASSWORD_PARAM_NAME,
                ObjectUtils.firstNonNull(settings.getSslTrustStorePassword(), JVM_TRUSTSTORE_DEFAULT_PASSWORD)
        );
    }


}
