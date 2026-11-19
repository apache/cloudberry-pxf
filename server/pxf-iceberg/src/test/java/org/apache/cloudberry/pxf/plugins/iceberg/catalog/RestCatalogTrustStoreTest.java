/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cloudberry.pxf.plugins.iceberg.catalog;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.cloudberry.pxf.api.model.RequestContext;
import org.apache.cloudberry.pxf.plugins.iceberg.IcebergSettings;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.catalog.Catalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects the REST catalog to an HTTPS server with a self-signed certificate.
 */
public class RestCatalogTrustStoreTest {

    private static final String PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    private Path keyStore;
    private Path trustStore;
    private HttpsServer server;
    private int configRequests;

    @BeforeEach
    public void setUp() throws Exception {
        keyStore = tempDir.resolve("server.jks");
        trustStore = tempDir.resolve("trust.jks");
        Path cert = tempDir.resolve("server.crt");
        keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1", "-validity", "1",
                "-keystore", keyStore.toString(), "-storetype", "JKS", "-storepass", PASSWORD, "-keypass", PASSWORD);
        keytool("-exportcert", "-alias", "server", "-keystore", keyStore.toString(),
                "-storepass", PASSWORD, "-file", cert.toString());
        keytool("-importcert", "-noprompt", "-alias", "server", "-file", cert.toString(),
                "-keystore", trustStore.toString(), "-storetype", "JKS", "-storepass", PASSWORD);

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(keyStore.toFile())) {
            ks.load(in, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/v1/config", exchange -> {
            configRequests++;
            byte[] body = "{\"defaults\":{},\"overrides\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void testCustomTrustStore() throws Exception {
        Catalog catalog = new RestCatalogFactory().create(settings(trustStore.toString(), PASSWORD));
        ((Closeable) catalog).close();

        assertEquals(1, configRequests);
    }

    @Test
    public void testDefaultTrustStorePasswordIsUsed() throws Exception {
        // the trust store above uses "changeit", the JVM default
        Catalog catalog = new RestCatalogFactory().create(settings(trustStore.toString(), null));
        ((Closeable) catalog).close();

        assertEquals(1, configRequests);
    }

    @Test
    public void testJvmTrustStoreRejectsSelfSignedCertificate() {
        Exception e = assertThrows(Exception.class, () -> new RestCatalogFactory().create(settings(null, null)));

        assertTrue(hasCause(e, SSLException.class), "expected an SSL error, got " + e);
        assertEquals(0, configRequests);
    }

    private IcebergSettings settings(String trustStorePath, String trustStorePassword) {
        Configuration configuration = new Configuration(false);
        configuration.set("iceberg.config.catalog.type", "REST");
        configuration.set("iceberg.config.catalog.name", "rest");
        configuration.set("iceberg.config.ssl.enabled", "true");
        if (trustStorePath != null) {
            configuration.set("iceberg.config.ssl.truststore.path", trustStorePath);
        }
        if (trustStorePassword != null) {
            configuration.set("iceberg.config.ssl.truststore.password", trustStorePassword);
        }
        // not "localhost": after a failed handshake on 127.0.0.1 the client would try ::1 and report
        // the refused connection instead of the TLS error
        configuration.set("iceberg.param.uri", "https://127.0.0.1:" + server.getAddress().getPort());
        RequestContext context = new RequestContext();
        context.setServerName("default");
        return IcebergSettings.create(configuration, context);
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static void keytool(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "keytool failed: " + output);
    }
}
