package com.giraone.kafka.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaProducerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerConfig.class);

    private final ApplicationProperties applicationProperties;

    public KafkaProducerConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    public SenderOptions<String, String> kafkaSenderOptions(KafkaProperties kafkaProperties) {

        final SenderOptions<String, String> basicSenderOptions = SenderOptions
            .create(kafkaProperties.buildProducerProperties());
        final SenderOptions<String, String> ret = basicSenderOptions
            .maxInFlight(256); // is default
        //if (ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            ret.scheduler(SchedulerConfig.scheduler);
        //}
        LOGGER.info("SenderOptions defined by bean of {}", this.getClass().getSimpleName());
        return ret;
    }

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(
        SenderOptions<String, String> kafkaSenderOptions) {

        return new ReactiveKafkaProducerTemplate<>(kafkaSenderOptions);
    }
}