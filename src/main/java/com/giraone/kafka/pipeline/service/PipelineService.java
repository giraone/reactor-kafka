package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.config.SchedulerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.util.Locale;

@Service
public class PipelineService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineService.class);

    private final ApplicationProperties applicationProperties;
    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    private final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    private final String topicOutput;

    public PipelineService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        this.applicationProperties = applicationProperties;
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.topicOutput = applicationProperties.getTopic2();
    }

    //------------------------------------------------------------------------------------------------------------------

    public void run_receive_flatMap_send(String... args) { // receive/flatMap/send

        if (!ApplicationProperties.MODE_PIPELINE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING PipelineService");
        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> {
                LOGGER.info(">>> k={}/v={}", receiverRecord.key(), receiverRecord.value());
                LOGGER.info("  > t={}/p={}/o={}", receiverRecord.topic(), receiverRecord.partition(), receiverRecord.receiverOffset().offset());
            })
            .flatMap(receiverRecord -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, receiverRecord.key(), receiverRecord.value());
                LOGGER.info(">>> k={}/v={}", producerRecord.key(), producerRecord.value());
                return reactiveKafkaProducerTemplate.send(SenderRecord.create(producerRecord, receiverRecord.receiverOffset()));
            })
            .doOnNext(this::ack)
            .subscribeOn(SchedulerConfig.scheduler)
            .subscribe();
    }

    @Override
    public void run(String... args) { // send(receive.map())

        if (!ApplicationProperties.MODE_PIPELINE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING PipelineService");
        // we have to trigger the consumption
        reactiveKafkaProducerTemplate
            .send(
                reactiveKafkaConsumerTemplate.receive()
                    .doOnNext(receiverRecord -> {
                        LOGGER.info(">>> k={}/v={}", receiverRecord.key(), receiverRecord.value());
                        LOGGER.info("  > t={}/p={}/o={}", receiverRecord.topic(), receiverRecord.partition(), receiverRecord.receiverOffset());
                    })
                    // pass receiverOffset as correlation metadata to commit on send
                    .map(receiverRecord -> SenderRecord.create(transformRecord(receiverRecord), receiverRecord.receiverOffset())))
            .doOnNext(this::ack)
            .subscribe();
    }

    private ProducerRecord<String, String> transformRecord(ReceiverRecord<String, String> receiverRecord) {

        final ProducerRecord<String, String> ret = new ProducerRecord<>(topicOutput, receiverRecord.key(), transform(receiverRecord.value()));
        LOGGER.info("<<< {}={}", ret.key(), ret.value());
        return ret;
    }

    private String transform(String input) {
        return input.toUpperCase(Locale.ROOT);
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {

        LOGGER.info("  < k={}/t={}/p={}/o={}", senderResult.correlationMetadata().offset(), senderResult.recordMetadata().topic(),
            senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
        if (applicationProperties.getCommitProperties().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit();
        }
    }
}