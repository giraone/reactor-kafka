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

    public KafkaProducerConfig(ApplicationProperties applicationProperties) {
        LOGGER.info("Output topic is: {}", applicationProperties.getTopicOutput());
    }

    @Bean
    public SenderOptions<String, String> kafkaSenderOptions(KafkaProperties kafkaProperties) {

        SenderOptions<String, String> basicSenderOptions = SenderOptions
            .create(kafkaProperties.buildProducerProperties());

        SenderOptions<String, String> ret = basicSenderOptions;
        LOGGER.info("SenderOptions defined by bean of {}", this.getClass().getSimpleName());
        return ret;
    }

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(
        SenderOptions<String, String> kafkaSenderOptions) {

        return new ReactiveKafkaProducerTemplate<>(kafkaSenderOptions);
    }
}