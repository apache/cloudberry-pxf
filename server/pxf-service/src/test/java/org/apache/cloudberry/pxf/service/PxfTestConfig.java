package org.apache.cloudberry.pxf.service;

import org.apache.cloudberry.pxf.api.configuration.PxfServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"org.apache.cloudberry.pxf.api"})
@EnableConfigurationProperties(PxfServerProperties.class)
public class PxfTestConfig {
}
