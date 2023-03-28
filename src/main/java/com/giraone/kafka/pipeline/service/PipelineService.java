package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.util.Locale;

@Service
public class PipelineService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineService.class);

        private final Scheduler scheduler = Schedulers.newBoundedElastic(
            Runtime.getRuntime().availableProcessors() - 1, Integer.MAX_VALUE, "schedulers");

    private final ApplicationProperties.CommitProperties commitProperties;
    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    private final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    private final String topicOutput;

    public PipelineService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        this.commitProperties = applicationProperties.getCommitProperties();
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.topicOutput = applicationProperties.getTopicOutput();
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void run(String... args) {

        LOGGER.info("STARTING Processor on {}", "x");
        // we have to trigger the consumption
        reactiveKafkaProducerTemplate
            .send(
                reactiveKafkaConsumerTemplate.receive()
                    .publishOn(scheduler)
                    .doOnNext(receiverRecord -> {
                        LOGGER.info(">>> {}/{}", receiverRecord.topic(), receiverRecord.partition());
                        LOGGER.info(" >> {}={}", receiverRecord.key(), receiverRecord.value());
                    })
                    .map(receiverRecord -> SenderRecord.create(transformRecord(receiverRecord), receiverRecord.receiverOffset())))
            .doOnNext(this::ack)
            .subscribe();
    }

    private ProducerRecord<String, String> transformRecord(ReceiverRecord<String, String> receiverRecord) {

        ProducerRecord<String, String> ret = new ProducerRecord<>(topicOutput, receiverRecord.key(), transform(receiverRecord.value()));
        LOGGER.info(" << {}={}",ret.key(), ret.value());
        return ret;
    }

    private String transform(String input) {
        return input.toUpperCase(Locale.ROOT);
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {

        LOGGER.info("<<< {}/{}",senderResult.recordMetadata().topic(), senderResult.recordMetadata().partition());
        if (commitProperties.isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit();
        }
    }
}