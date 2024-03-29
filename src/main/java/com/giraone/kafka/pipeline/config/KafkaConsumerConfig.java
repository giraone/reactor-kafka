package com.giraone.kafka.pipeline.config;

import com.giraone.kafka.pipeline.config.properties.KafkaConsumerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import reactor.kafka.receiver.MicrometerConsumerListener;
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
        LOGGER.info("Mode = {}", applicationProperties.getMode());
        if (applicationProperties.getMode().startsWith(ApplicationProperties.MODE_PIPE)) {
            LOGGER.info("GroupId of service PIPE is = {}.", applicationProperties.getConsumer().getGroupId());
            topicInput = applicationProperties.getTopicA();
            LOGGER.info("Input topic of service PIPE = {}.", topicInput);
        } else if (applicationProperties.getMode().startsWith(ApplicationProperties.MODE_CONSUME)) {
            LOGGER.info("GroupId of service CONSUME = {}.", applicationProperties.getConsumer().getGroupId());
            topicInput = applicationProperties.getTopicB();
            LOGGER.info("Input topic of service CONSUME = {}.", topicInput);
        } else {
            LOGGER.info("Service PRODUCE has no input topic.");
            topicInput = null;
        }
    }

    @Bean
    public ReceiverOptions<String, String> kafkaReceiverOptions(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {

        final Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        // Always use the new StickyAssignor, to keep the assignments as balanced as possible
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        // Group ID depends on the "mode"
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationProperties.getConsumer().getGroupId());
        // Poll properties
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, applicationProperties.getConsumer().getMaxPollRecords());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) applicationProperties.getConsumer().getMaxPollInterval().toMillis());
        // Fetch properties
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, applicationProperties.getConsumer().getFetchMaxBytes());
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, applicationProperties.getConsumer().getMaxPartitionFetchBytes());
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, (int) applicationProperties.getConsumer().getFetchMaxWaitMs().toMillis());

        final ReceiverOptions<String, String> basicReceiverOptions = ReceiverOptions.create(props);

        final KafkaConsumerProperties consumerProperties = applicationProperties.getConsumer();
        if (consumerProperties.isAutoCommit()) {
            basicReceiverOptions
                .commitInterval(consumerProperties.getCommitInterval())
                .commitBatchSize(consumerProperties.getCommitBatchSize());
            if (consumerProperties.getCommitRetryInterval() != null) {
                basicReceiverOptions.commitRetryInterval(consumerProperties.getCommitRetryInterval());
            }
        } else {
            // See https://projectreactor.io/docs/kafka/release/reference/#kafka-source
            basicReceiverOptions
                .commitInterval(Duration.ZERO) // Disable periodic commits
                .commitBatchSize(0); // Disable commits by batch size
        }

        // No subscription, when it is the sink (producer only)
        if (ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            return basicReceiverOptions;
        } else if (applicationProperties.getMode().endsWith("ExactlyOnce")) {
            basicReceiverOptions.consumerProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        }

        final List<String> inputTopics = topicInput != null ? List.of(topicInput) : List.of();
        final ReceiverOptions<String, String> ret = basicReceiverOptions
            .subscription(inputTopics)
            .consumerListener(new MicrometerConsumerListener(meterRegistry)) // we want standard Kafka metrics
            ;
        LOGGER.info("ReceiverOptions defined by bean of {} with topics={}, commitInterval={}, commitBatchSize={}, commitRetryInterval={}"
                + ", {}={}, {}={}, {}={}",
            this.getClass().getSimpleName(), inputTopics, ret.commitInterval(), ret.commitBatchSize(), ret.commitRetryInterval(),
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, props.get(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG),
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, props.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG),
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG, props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)
        );
        LOGGER.info("Other Kafka configs: {}={}",
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, props.get(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG)
        );
        return ret;
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate(
        ReceiverOptions<String, String> kafkaReceiverOptions) {

        return new ReactiveKafkaConsumerTemplate<>(kafkaReceiverOptions);
    }
}