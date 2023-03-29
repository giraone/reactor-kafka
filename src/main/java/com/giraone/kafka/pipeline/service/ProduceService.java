package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.config.SchedulerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.SenderRecord;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;

@Service
public class ProduceService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProduceService.class);

    private final ApplicationProperties applicationProperties;
    private final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    private final String topicOutput;

    public ProduceService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        this.applicationProperties = applicationProperties;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.topicOutput = applicationProperties.getTopic1();
    }

    //------------------------------------------------------------------------------------------------------------------

    public void run_withFlatMap(String... args) {

        if (!ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING ProduceService");
        source(Duration.ofMillis(100), Integer.MAX_VALUE)
            .flatMap(tuple -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                LOGGER.info(">>> k={}/v={}", producerRecord.key(), producerRecord.value());
                return reactiveKafkaProducerTemplate.send(SenderRecord.create(producerRecord, tuple.getT1()));
            })
            .doOnNext(senderResult -> {
                LOGGER.info("  > t={}/p={}/o={}", senderResult.recordMetadata().topic(),
                    senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
            })
            .doOnError(e -> LOGGER.error("Send failed", e))
            .subscribeOn(SchedulerConfig.scheduler)
            .subscribe();
    }

    @Override
    public void run(String... args) {

        if (!ApplicationProperties.MODE_PRODUCE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING KafkaSinkService");
        reactiveKafkaProducerTemplate.send(source(Duration.ofMillis(1), Integer.MAX_VALUE)
                .map(tuple -> {
                    ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                    LOGGER.info(">>> k={}/v={}", producerRecord.key(), producerRecord.value());
                    return SenderRecord.create(producerRecord, tuple.getT1());
                })
            )
            .doOnNext(senderResult -> {
                LOGGER.info("  > k={}/t={}/p={}/o={}", senderResult.correlationMetadata(), senderResult.recordMetadata().topic(),
                    senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
            })
            .doOnError(e -> LOGGER.error("Send failed", e))
            .subscribeOn(SchedulerConfig.scheduler)
            .subscribe();
    }

    protected static Flux<Tuple2<String, String>> source(Duration delay, int limit) {

        int s = (int) (System.currentTimeMillis() / 1000L);
        return Flux.range(s,limit - s)
            .delayElements(delay)
            .map(nr -> Tuples.of(Long.toString(nr), Long.toString(System.currentTimeMillis())));
    }
}