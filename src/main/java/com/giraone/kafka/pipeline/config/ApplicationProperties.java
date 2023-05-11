package com.giraone.kafka.pipeline.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import reactor.core.publisher.Hooks;
import reactor.util.retry.Retry;

import java.time.Duration;

@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
@Setter
@Getter
@NoArgsConstructor
@ToString
public class ApplicationProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationProperties.class);

    private static final int KAFKA_RETRY_DEFAULT_NUMBER_OF_ATTEMPTS = 2;

    public static final String DEFAULT_TOPIC_1 = "topic-1";
    public static final String DEFAULT_TOPIC_2 = "topic-2";
    public static final String MODE_PIPE = "Pipe";
    public static final String MODE_CONSUME = "Consume";
    public static final String MODE_PRODUCE = "Produce";

    /**
     * Log the configuration to the log on startup
     */
    private boolean showConfigOnStartup = true;
    /**
     * WebFlux Hooks.onOperatorDebug() to get full stack traces. Should not be used in production.
     */
    private boolean debugHooks;
    /**
     * Enable reactor-tools ReactorDebugAgent to get stack traces. Can be used also in production.
     */
    private boolean debugAgent;
    /**
     * Mode: Produce, PipeSendReceive, PipeReceiveSend, PipePartitioned, PipeExactlyOnce, Consume
     */
    private String mode = MODE_PIPE + "Partitioned";
    /**
     * Input topic.
     */
    private String topic1 = DEFAULT_TOPIC_1;
    /**
     * Input topic.
     */
    private String topic2 = DEFAULT_TOPIC_2;
    /**
     * Interval for producer service. E.g. 1ms => 64 Events per second.
     */
    private Duration produceInterval = Duration.ofMillis(1);
    /**
     * Working interval for pipe service. E.g. 10ms: Transform step wil wait 10ms. Can be null.
     */
    private Duration transformInterval = null;
    /**
     * Kafka producer properties.
     */
    private ProducerProperties producer = new ProducerProperties();
    /**
     * Kafka consumer properties.
     */
    private ConsumerProperties consumer = new ConsumerProperties();


    @SuppressWarnings("java:S2629") // invoke conditionally
    @PostConstruct
    private void startup() {
        if (this.showConfigOnStartup) {
            LOGGER.info(this.toString());
        }
        if (this.debugHooks) {
            LOGGER.warn("WEBFLUX DEBUG: Enabling Hooks.onOperatorDebug. DO NOT USE IN PRODUCTION!");
            Hooks.onOperatorDebug();
            if (this.debugAgent) {
                LOGGER.error("WEBFLUX DEBUG: DO NOT USE debug-hooks together with debug-agent!");
            }
        } else if (this.debugAgent) {
            long s = System.currentTimeMillis();
            LOGGER.info("WEBFLUX DEBUG: Enabling ReactorDebugAgent. Init may take 20 seconds! May slow down runtime performance (only) slightly.");
            // See: https://github.com/reactor/reactor-tools and https://github.com/reactor/reactor-core/tree/main/reactor-tools
            // ReactorDebugAgent.init();
            // ReactorDebugAgent.processExistingClasses();
            LOGGER.info("WEBFLUX DEBUG: ReactorDebugAgent.processExistingClasses finished in {} ms", System.currentTimeMillis() - s);
        }
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @ToString
    public static class ProducerProperties {
        /** all = quorum (default), 1 = leader only, 0 = no ack */
        private String acks = "all";
        /** for performance increase to 100000–200000 (default 16384) */
        private int batchSize = 16384;
        /** **/
        private Duration deliveryTimeout = Duration.ofMinutes(5);
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @ToString
    public static class ConsumerProperties {
        /**
         * Number of consumer threads, when PipePartitionedService is used
         */
        private int threads = 4;
        /**
         * Flag, whether auto-commit is used - default=false. If true commitInterval/commitBatchSize are used..
         **/
        private boolean autoCommit = true;
        /**
         * Configures commit interval for automatic commits.
         * At least one commit operation is attempted within this interval if records are consumed and acknowledged.
         */
        private Duration commitInterval = Duration.ofSeconds(1L);
        /**
         * Configures commit batch size for automatic commits.
         * At least one commit operation is attempted when the number of acknowledged uncommitted offsets reaches this batch size.
         */
        private int commitBatchSize = 10;
        /**
         * Configures the retry commit interval for commits that fail with non-fatal RetriableCommitFailedException.
         */
        private Duration commitRetryInterval = null;
        /**
         * The maximum number of records returned in a single call to poll(). Default = 500.
         * Note, that <code>max.poll.records</code> does not impact the underlying fetching behavior.
         * The consumer will cache the records from each fetch request and returns them incrementally from each poll.";
         */
        private int maxPollRecords = 100;
        /**
         * The maximum delay between invocations of poll() when using consumer group management. Default = 30 seconds.
         * This places an upper bound on the amount of time that the consumer can be idle before fetching more records.";
         */
        private Duration maxPollInterval = Duration.ofSeconds(10);
        /**
         * Retries, when inbound flux (consumer) fails.
         * Since in reactive streams an error represents a terminal signal, any error signal emitted in the inbound
         * Flux will cause the subscription to be cancelled and effectively cause the consumer to shut down.
         * This can be mitigated by using this retry.
         */
        private RetrySpecificationKafka retrySpecification = new RetrySpecificationKafka();
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @ToString
    public static class RetrySpecificationKafka {
        /**
         * the maximum number of retry attempts to allow. Default = 3.
         */
        private long maxAttempts = KAFKA_RETRY_DEFAULT_NUMBER_OF_ATTEMPTS;
        /**
         * the minimum Duration for the first backoff, when exponential backoff is used. Default = 10 seconds.
         */
        private Duration backoff = Duration.ofSeconds(10);

        public Retry toRetry() {
            return Retry.backoff(maxAttempts, backoff);
        }
    }
}
