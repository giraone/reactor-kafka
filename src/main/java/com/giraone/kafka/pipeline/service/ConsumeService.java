package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverRecord;

@Service
public class ConsumeService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeService.class);

    private final ApplicationProperties applicationProperties;
    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    private final CounterService counterService;

    public ConsumeService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        CounterService counterService
    ) {
        this.applicationProperties = applicationProperties;
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.counterService = counterService;
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void run(String... args) {

        if (!ApplicationProperties.MODE_CONSUME.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING ConsumeService");
        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(this::consume)
            .doOnNext(this::ack)
            .subscribe();
    }

    protected void consume(ReceiverRecord<String, String> receiverRecord) {

        counterService.logRate("RCV", receiverRecord.partition(), receiverRecord.offset());
    }
    protected void ack(ReceiverRecord<String, String> receiverRecord) {

        if (applicationProperties.getConsumerProperties().isAutoCommit()) {
            receiverRecord.receiverOffset().acknowledge();
        } else {
            receiverRecord.receiverOffset().commit().block();
        }
        counterService.logRate("ACK", receiverRecord.partition(), receiverRecord.offset());
    }
}