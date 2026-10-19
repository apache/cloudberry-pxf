package org.apache.cloudberry.pxf.plugins.iceberg;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ComponentScan("org.apache.cloudberry.pxf.plugins.iceberg")
public class IcebergAutoConfiguration {

    @Bean
    public Retry icebergAppendFilesRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(100)
                .waitDuration(Duration.ofMillis(500))
                // the commit may have been applied, retrying it could append the same files twice
                .ignoreExceptions(CommitStateUnknownException.class)
                .build();
        return RetryRegistry.of(config).retry("iceberg_append_files");
    }
}
