package com.giraone.kafka.pipeline.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsConfig.class);

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
        @Value("${spring.application.name}") String applicationName,
        @Value("${application.mode}") String mode
    ) {
        LOGGER.info("Name = {}. Registered as \"application\".", applicationName);
        LOGGER.info("Mode = {}. Registered as \"mode\".", mode);
        return registry -> registry.config()
            .commonTags("application", applicationName)
            .commonTags("mode", mode);
    }
}
