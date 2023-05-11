package com.giraone.kafka.pipeline.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.kafka.sender.MicrometerProducerListener;
import reactor.kafka.sender.SenderOptions;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerConfig.class);

    private final ApplicationProperties applicationProperties;

    public KafkaProducerConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    public SenderOptions<String, String> kafkaSenderOptions(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {

        final Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, applicationProperties.getProducer().getAcks());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(applicationProperties.getProducer().getBatchSize()));
        //props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, applicationProperties.getProducer().getDeliveryTimeout());
        if (applicationProperties.getMode().endsWith("Transactional")) {
            final String transactionalId = "PipeTxn";
            LOGGER.info("TRANSACTIONS: transactionalId={}", transactionalId);
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        }

        final SenderOptions<String, String> basicSenderOptions = SenderOptions.create(props);

        final SenderOptions<String, String> ret = basicSenderOptions
            .maxInFlight(1) // to keep ordering, prevent duplicate messages (and avoid data loss)
            .producerListener(new MicrometerProducerListener(meterRegistry)) // we want standard Kafka metrics
            ;

        // https://stackoverflow.com/a/69904417 : ...a single producer instance across threads will generally be faster...
//        if (ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
//            final int threads = Runtime.getRuntime().availableProcessors() - 1;
//            final Scheduler scheduler = Schedulers.newParallel("parallel",threads);
//            ret.scheduler(scheduler); // the producer should be as fast (parallel) as possible
//            LOGGER.info("Producer running with {} parallel threads", threads);
//        }
        LOGGER.info("SenderOptions defined by bean of {} with scheduler={}, stopOnError={}, maxInFlight={}, closeTimeout={}"
                + ", {}={}, {}={}, {}={}, {}={}, {}={}, {}={}",
            this.getClass().getSimpleName(), ret.scheduler(), ret.stopOnError(), ret.maxInFlight(), ret.closeTimeout(),
            ProducerConfig.ACKS_CONFIG, ret.producerProperties().get(ProducerConfig.ACKS_CONFIG),
            ProducerConfig.BATCH_SIZE_CONFIG, ret.producerProperties().get(ProducerConfig.BATCH_SIZE_CONFIG),
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, ret.producerProperties().get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG),
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, ret.producerProperties().get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG),
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, ret.producerProperties().get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG),
            ProducerConfig.TRANSACTIONAL_ID_CONFIG, ret.producerProperties().get(ProducerConfig.TRANSACTIONAL_ID_CONFIG)
        );
        return ret;
    }

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(
        SenderOptions<String, String> kafkaSenderOptions) {

        return new ReactiveKafkaProducerTemplate<>(kafkaSenderOptions);
    }
}