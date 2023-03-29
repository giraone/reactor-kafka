package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.config.SchedulerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;

@Service
public class ConsumeService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeService.class);

    private final ApplicationProperties applicationProperties;
    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;

    public ConsumeService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        this.applicationProperties = applicationProperties;
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void run(String... args) {

        if (!ApplicationProperties.MODE_CONSUME.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING ConsumeService");
        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> {
                LOGGER.info(">>> k={}/v={}", receiverRecord.key(), receiverRecord.value());
                LOGGER.info("  > t={}/p={}/o={}", receiverRecord.topic(), receiverRecord.partition(), receiverRecord.receiverOffset().offset());
            })
            .doOnNext(this::ack)
            .subscribeOn(SchedulerConfig.scheduler)
            .subscribe();
    }

    private void ack(ReceiverRecord<String,String> receiverRecord) {
        if (applicationProperties.getCommitProperties().isAutoCommit()) {
            receiverRecord.receiverOffset().acknowledge();
        } else {
            receiverRecord.receiverOffset().commit().block();
        }
    }
}