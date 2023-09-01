package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Locale;

public abstract class AbstractPipeService extends AbstractService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPipeService.class);

    protected final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    protected final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;


    protected final Duration delay; // How long does the pure processing take?
    protected final Retry retry;

    public AbstractPipeService(ApplicationProperties applicationProperties,
                               ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
                               ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
                               CounterService counterService) {
        super(applicationProperties, counterService);
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;

        this.delay = applicationProperties.getProcessingTime();
        this.retry = applicationProperties.getConsumer().getRetrySpecification().toRetry();
    }

    protected abstract void start();

    @Override
    public void run(String... args) {

        if (!(applicationProperties.getMode() + "Service").equalsIgnoreCase(this.getClass().getSimpleName())) {
            return;
        }
        LOGGER.info("STARTING {}", this.getClass().getSimpleName());
        this.start();
    }

    /**
     * The pipeline task, that may take some time (defined by APPLICATION_PROCESSING_TIME) for processing an input.
     */
    protected Mono<SenderRecord<String, String, ReceiverOffset>> process(ReceiverRecord<String,String> inputRecord) {
        return Mono.delay(applicationProperties.getProcessingTime())
            .map(ignored -> coreProcess(inputRecord.value()))
            // pass receiverOffset as correlation metadata to commit on send
            .map(outputValue -> SenderRecord.create(new ProducerRecord<>(topicOutput, inputRecord.key(), outputValue), inputRecord.receiverOffset()));
    }

    /**
     * The pipeline task, that may take some time (defined by APPLICATION_PROCESSING_TIME) for processing an input.
     * This version is for ConsumerRecord instead of ReceiverRecord
     */
    protected Mono<SenderRecord<String, String, ReceiverOffset>> process(ConsumerRecord<String,String> inputRecord) {
        return Mono.delay(applicationProperties.getProcessingTime())
            .map(ignored -> coreProcess(inputRecord.value()))
            // pass receiverOffset as correlation metadata to commit on send
            .map(outputValue -> SenderRecord.create(new ProducerRecord<>(topicOutput, inputRecord.key(), outputValue), null /* no correlation meta data */));
    }

    /**
     * The core pipeline task, without the event metadata (message key) and without additional waiting time.
     * Here a simple convert toUpperCase.
     */
    protected String coreProcess(String input) {
        counterService.logRate("TASK");
        return input.toUpperCase(Locale.ROOT);
    }
}
