package com.giraone.kafka.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.List;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final String topicInput;
    private final ApplicationProperties.CommitProperties commitProperties;

    public KafkaConsumerConfig(ApplicationProperties applicationProperties) {
        commitProperties = applicationProperties.getCommitProperties();
        topicInput = applicationProperties.getTopicInput();
        LOGGER.info("Input topic is: {}", topicInput);
    }

    @Bean
    public ReceiverOptions<String, String> kafkaReceiverOptions(KafkaProperties kafkaProperties) {

        ReceiverOptions<String, String> basicReceiverOptions = ReceiverOptions
            .create(kafkaProperties.buildConsumerProperties());

        if (commitProperties.isAutoCommit()) {
            basicReceiverOptions
                .commitInterval(commitProperties.getCommitInterval())
                .commitBatchSize(commitProperties.getCommitBatchSize());
        } else {
            // See https://projectreactor.io/docs/kafka/release/reference/#kafka-source
            basicReceiverOptions
                .commitInterval(Duration.ZERO) // Disable periodic commits
                .commitBatchSize(0); // Disable commits by batch size
        }

        final List<String> allTopics = List.of(topicInput);
        ReceiverOptions<String, String> ret = basicReceiverOptions.subscription(allTopics);
        LOGGER.info("ReceiverOptions defined by bean of {} with topics {}", this.getClass().getSimpleName(), allTopics);
        return ret;
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate(
        ReceiverOptions<String, String> kafkaReceiverOptions) {

        LOGGER.info("Subscription with group id \"{}\" to {}", kafkaReceiverOptions.groupId(), kafkaReceiverOptions.subscriptionTopics());
        return new ReactiveKafkaConsumerTemplate<>(kafkaReceiverOptions);
    }
}