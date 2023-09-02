package com.giraone.kafka.pipeline.config;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
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

    public static final String DEFAULT_TOPIC_A = "a1";
    public static final String DEFAULT_TOPIC_B = "b1";
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
     * Mode: ProduceStandard, ProduceFlatMap, ProduceTransactional, PipeSendReceive, PipeReceiveSend, PipePartitioned, PipeExactlyOnce, Consume
     */
    private String mode = MODE_PIPE;
    /**
     * GroupId: PipeSendReceive, PipeReceiveSend, PipePartitioned, PipeExactlyOnce, Consume
     */
    private String groupId = mode;
    /**
     * First topic between producer and pipe.
     */
    private String topicA = DEFAULT_TOPIC_A;
    /**
     * Second topic between producer and pipe.
     */
    private String topicB = DEFAULT_TOPIC_B;
    /**
     * Time interval for producer service after which a new event is emitted. With produceInterval=100ms, there should be
     * approx. 10 events per second.
     * Default is 100ms.
     */
    private Duration produceInterval = Duration.ofMillis(100);
    /**
     * Processing time for pipe service. The transform step will take (wait) this amount of time.
     * Default is 10ms.
     */
    private Duration processingTime = Duration.ofMillis(10);
    /**
     * Kafka producer properties.
     */
    private ProducerProperties producer = new ProducerProperties();
    /**
     * Kafka consumer properties.
     */
    private ConsumerProperties consumer = new ConsumerProperties();

    private HostAndPort loki = new HostAndPort("localhost", 3100);

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
        /**
         * all = quorum (default), 1 = leader only, 0 = no ack
         */
        private String acks = "all";
        /**
         * For more performance increase to 100000–200000. Default = 16384.
         */
        private int batchSize = 16384;
        /**
         * Maximum number of events, that are produced. Default = 1_000_000.
         **/
        private int maxNumberOfEvents = 1_000_000;
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
        private boolean autoCommit = false;
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
         * The maximum number of records returned in a single call to poll().
         * Kafka default = 500.
         * Note, that <code>max.poll.records</code> does not impact the underlying fetching behavior.
         * The consumer will cache the records from each fetch request and returns them incrementally from each poll.
         */
        private int maxPollRecords = 500; // Kafka default = 500
        /**
         * The maximum delay between invocations of poll() in seconds when using consumer group management.
         * Kafka default = 30 seconds.
         * This places an upper bound on the amount of time that the consumer can be idle before fetching more records.
         */
        private Duration maxPollInterval = Duration.ofSeconds(30);
        /**
         * The maximum amount of data the server should return for a fetch request.
         * Kafka default = 50MByte.
         * Records are fetched in batches by the consumer, and if the first record batch in the first non-empty partition
         * of the fetch is larger than this value, the record batch will still be returned to ensure that the consumer
         * can make progress. As such, this is not a absolute maximum.
         * Note that the consumer performs multiple fetches in parallel.
         */
        private int fetchMaxBytes = 52428800; // Kafka default = 52428800
        /**
         * The maximum amount of data per-partition the server will return.
         * Kafka default = 1MByte.
         * Records are fetched in batches by the consumer.
         * If the first record batch in the first non-empty partition of the fetch is larger than this limit, the batch
         * will still be returned to ensure that the consumer can make progress.
         * See fetch.max.bytes for limiting the consumer request size.
         */
        private int maxPartitionFetchBytes = 1048576; // Kafka default = 1048576
        /**
         * The maximum amount of time the server will block before answering the fetch request if there isn’t sufficient
         * data to immediately satisfy the requirement given by fetch.min.bytes.
         * Kafka default = 500ms.
         */
        private Duration fetchMaxWaitMs = Duration.ofMillis(500); // Kafka default = 500ms
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


    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class HostAndPort {
        private String host = "localhost";
        private int port = 3100;
    }
}
