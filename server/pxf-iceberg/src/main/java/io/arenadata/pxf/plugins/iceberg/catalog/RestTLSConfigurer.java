package io.arenadata.pxf.plugins.iceberg.catalog;

import org.apache.iceberg.rest.auth.TLSConfigurer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;

public class RestTLSConfigurer implements TLSConfigurer {

    public static final String TRUSTSTORE_PATH_PARAM_NAME = "tls.truststore.path",
                               TRUSTSTORE_PASSWORD_PARAM_NAME = "tls.truststore.password",
                               TRUSTSTORE_TYPE_PARAM_NAME = "tls.truststore.type";

    private SSLContext sslContext;

    @Override
    public void initialize(Map<String, String> properties) {
        try {
            String trustStorePath     = properties.get(TRUSTSTORE_PATH_PARAM_NAME);
            String trustStorePassword = properties.get(TRUSTSTORE_PASSWORD_PARAM_NAME);
            String trustStoreType     = properties.getOrDefault(TRUSTSTORE_TYPE_PARAM_NAME, "JKS");

            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            try (FileInputStream fis = new FileInputStream(trustStorePath)) {
                trustStore.load(fis, trustStorePassword.toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(trustStore);

            this.sslContext = SSLContext.getInstance("TLS");
            this.sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TLSConfigurer", e);
        }
    }

    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

}
