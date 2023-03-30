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
import reactor.util.retry.Retry;

import java.util.Locale;

@Service
public class PipelineService implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineService.class);

    // Worker threads


    private final ApplicationProperties applicationProperties;
    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    private final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    private final CounterService counterService;

    private final String topicOutput;
    private final Retry retry;
    private final Scheduler scheduler;

    public PipelineService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        CounterService counterService
    ) {
        this.applicationProperties = applicationProperties;
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.counterService = counterService;
        this.topicOutput = applicationProperties.getTopic2();
        this.retry = applicationProperties.getConsumerProperties().getRetrySpecification().toRetry();
        this.scheduler = Schedulers.newParallel("worker",
            applicationProperties.getConsumerProperties().getThreads(), true);
    }

    //------------------------------------------------------------------------------------------------------------------

    public void run1(String... args) { // send(receive().map(r->SenderRecord))

        if (!ApplicationProperties.MODE_PIPELINE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING PipelineService");
        reactiveKafkaProducerTemplate
            .send(
                reactiveKafkaConsumerTemplate.receive()
                    .retryWhen(retry)
                    .doOnNext(receiverRecord -> counterService.logRate("RCV", receiverRecord.partition()))
                    // pass receiverOffset as correlation metadata to commit on send
                    .map(receiverRecord -> SenderRecord.create(transformRecord(receiverRecord), receiverRecord.receiverOffset())))
            .doOnNext(this::ack)
            .subscribe();
    }

    @Override
    public void run(String... args) { // receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())

        if (!ApplicationProperties.MODE_PIPELINE.equals(applicationProperties.getMode())) {
            return;
        }
        LOGGER.info("STARTING PipelineService");

        reactiveKafkaConsumerTemplate.receive()
            .retryWhen(retry)
            // Concurrent Processing with Partition-Based Ordering
            .groupBy(receiverRecord -> receiverRecord.receiverOffset().topicPartition())
            .flatMap(partitionFlux ->
                partitionFlux.publishOn(scheduler)
                    .doOnNext(receiverRecord -> counterService.logRate("RCV", receiverRecord.partition()))
                    .flatMap(receiverRecord -> {
                        final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, receiverRecord.key(), receiverRecord.value());
                        return reactiveKafkaProducerTemplate.send(SenderRecord.create(producerRecord, receiverRecord.receiverOffset()));
                    })
                    .sample(applicationProperties.getConsumerProperties().getCommitInterval()) // Commit periodically
                    .concatMap(senderResult -> senderResult.correlationMetadata().commit()))
            .subscribe();
    }

    private ProducerRecord<String, String> transformRecord(ReceiverRecord<String, String> receiverRecord) {

        final ProducerRecord<String, String> ret = new ProducerRecord<>(topicOutput, receiverRecord.key(), transform(receiverRecord.value()));
        if (applicationProperties.getTransformInterval() != null) {
            try {
                Thread.sleep(applicationProperties.getTransformInterval().toMillis());
                counterService.logRate("PRC", receiverRecord.partition());
            } catch (InterruptedException e) {
                LOGGER.error("Transform interrupted!");
            }
        }
        LOGGER.debug("<<< {}={}", ret.key(), ret.value());
        return ret;
    }

    private String transform(String input) {
        return input.toUpperCase(Locale.ROOT);
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {

        LOGGER.debug("  < k={}/t={}/p={}/o={}", senderResult.correlationMetadata().offset(), senderResult.recordMetadata().topic(),
            senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
        if (applicationProperties.getConsumerProperties().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
        counterService.logRate("ACK", senderResult.correlationMetadata().topicPartition().partition());
    }
}