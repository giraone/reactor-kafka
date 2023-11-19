package com.giraone.kafka.pipeline.config;

import com.giraone.kafka.pipeline.config.properties.KafkaConsumerProperties;
import com.giraone.kafka.pipeline.config.properties.KafkaProducerProperties;
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
     * Mode: ProduceSendSource, ProduceFlatMap, ProduceTransactional, PipeSendReceive, PipeReceiveSend, PipePartitioned, PipeExactlyOnce, PipeDedup, Consume
     */
    private String mode = MODE_PIPE;
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
    private KafkaProducerProperties producer = new KafkaProducerProperties();
    /**
     * Kafka consumer properties.
     */
    private KafkaConsumerProperties consumer = new KafkaConsumerProperties(mode);
    /**
     * LOKI server properties.
     */
    private HostAndPort loki = new HostAndPort("localhost", 3100);
    /**
     * Kafka producer properties.
     */
    private ProducerVariables producerVariables = new ProducerVariables();

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
    public static class ProducerVariables {
        /**
         * Maximum number of events, that are produced. Default = 1_000_000.
         **/
        private int maxNumberOfEvents = 1_000_000;
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
