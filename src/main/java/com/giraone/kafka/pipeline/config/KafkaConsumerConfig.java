package com.giraone.kafka.pipeline.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.StickyAssignor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final String topicInput;
    private final ApplicationProperties applicationProperties;

    public KafkaConsumerConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        LOGGER.info("Mode is: {}", applicationProperties.getMode());
        this.topicInput = ApplicationProperties.MODE_PIPELINE.equals(applicationProperties.getMode())
            ? applicationProperties.getTopic1()
            : applicationProperties.getTopic2();
        LOGGER.info("Input topic is: {}", topicInput);
    }

    @Bean
    public ReceiverOptions<String, String> kafkaReceiverOptions(KafkaProperties kafkaProperties) {

        final Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        // Always use the new StickyAssignor, to keep the assignments as balanced as possible
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, StickyAssignor.class.getName());
        // Group ID depends on the "mode"
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationProperties.getMode());

        final ReceiverOptions<String, String> basicReceiverOptions = ReceiverOptions
            .create(props);

        if (applicationProperties.getCommitProperties().isAutoCommit()) {
            basicReceiverOptions
                .commitInterval(applicationProperties.getCommitProperties().getCommitInterval())
                .commitBatchSize(applicationProperties.getCommitProperties().getCommitBatchSize());
        } else {
            // See https://projectreactor.io/docs/kafka/release/reference/#kafka-source
            basicReceiverOptions
                .commitInterval(Duration.ZERO) // Disable periodic commits
                .commitBatchSize(0); // Disable commits by batch size
        }

        // No subscription, when it is the sink (producer only)
        if (ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            return basicReceiverOptions;
        }
        final List<String> allTopics = List.of(topicInput);
        final ReceiverOptions<String, String> ret = basicReceiverOptions.subscription(allTopics);
        LOGGER.info("ReceiverOptions defined by bean of {} with subscribed topics={}", this.getClass().getSimpleName(), allTopics);
        return ret;
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate(
        ReceiverOptions<String, String> kafkaReceiverOptions) {

        return new ReactiveKafkaConsumerTemplate<>(kafkaReceiverOptions);
    }
}