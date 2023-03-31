package com.giraone.kafka.pipeline.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.MicrometerProducerListener;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaProducerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerConfig.class);

    private final ApplicationProperties applicationProperties;

    public KafkaProducerConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    public SenderOptions<String, String> kafkaSenderOptions(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {

        final SenderOptions<String, String> basicSenderOptions = SenderOptions
            .create(kafkaProperties.buildProducerProperties());

        final MicrometerProducerListener listener = new MicrometerProducerListener(meterRegistry);

        final SenderOptions<String, String> ret = basicSenderOptions
            .maxInFlight(1) // to keep ordering, prevent duplicate messages (and avoid data loss)
            .producerListener(listener) // we want standard Kafka metrics
            ;

        if (applicationProperties.getMode().endsWith("Transactional")) {
            ret.producerProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "PipeTxn");
        } else if (ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            final Scheduler scheduler = Schedulers.newParallel("parallel",
                Runtime.getRuntime().availableProcessors() - 1);
            ret.scheduler(scheduler); // the producer should be as fast (parallel) as possible
        }
        LOGGER.info("SenderOptions defined by bean of {} with stopOnError={}, maxInFlight={},closeTimeout={}",
            this.getClass().getSimpleName(), ret.stopOnError(), ret.maxInFlight(), ret.closeTimeout());
        return ret;
    }

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(
        SenderOptions<String, String> kafkaSenderOptions) {

        return new ReactiveKafkaProducerTemplate<>(kafkaSenderOptions);
    }
}