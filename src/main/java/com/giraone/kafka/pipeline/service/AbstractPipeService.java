package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractPipeService extends AbstractService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPipeService.class);

    protected final AtomicInteger starts = new AtomicInteger();

    protected final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    protected final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    protected final Scheduler scheduler;
    protected final String topicInput;
    protected final String topicOutput;
    protected final Duration delay; // How long does the pure processing take?
    protected final Retry retry;

    public AbstractPipeService(ApplicationProperties applicationProperties,
                               CounterService counterService,
                               ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
                               ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        super(applicationProperties, counterService);
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.scheduler = applicationProperties.getConsumer().buildScheduler();
        this.topicInput = applicationProperties.getTopicA();
        this.topicOutput = applicationProperties.getTopicB();
        this.delay = applicationProperties.getProcessingTime();
        this.retry = applicationProperties.getConsumer().getRetrySpecification().toRetry();
    }

    protected abstract void start();

    protected String getTopicInput() {
        return topicInput;
    }

    protected String getTopicOutput() {
        return topicOutput;
    }

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
    protected Mono<SenderRecord<String, String, ReceiverOffset>> process(ReceiverRecord<String, String> inputRecord) {
        return Mono.delay(applicationProperties.getProcessingTime())
            .map(ignored -> coreProcess(inputRecord.value()))
            // pass receiverOffset as correlation metadata to commit on send
            .map(outputValue -> SenderRecord.create(new ProducerRecord<>(getTopicOutput(), inputRecord.key(), outputValue), inputRecord.receiverOffset()));
    }

    /**
     * The pipeline task, that may take some time (defined by APPLICATION_PROCESSING_TIME) for processing an input.
     * This version is for ConsumerRecord instead of ReceiverRecord
     */
    protected Mono<SenderRecord<String, String, ReceiverOffset>> process(ConsumerRecord<String, String> inputRecord) {
        return Mono.delay(applicationProperties.getProcessingTime())
            .map(ignored -> coreProcess(inputRecord.value()))
            // pass receiverOffset as correlation metadata to commit on send
            .map(outputValue -> SenderRecord.create(new ProducerRecord<>(getTopicOutput(), inputRecord.key(), outputValue), null /* no correlation meta data */));
    }

    /**
     * The core pipeline task, without the event metadata (message key) and without additional waiting time.
     * Here a simple convert toUpperCase.
     */
    protected String coreProcess(String input) {
        counterService.logRateProcessed();
        return input.toUpperCase(Locale.ROOT);
    }

    protected void restartMainLoopOnError(Throwable throwable) {
        counterService.logMainLoopError(throwable);
        // We do not re-subscribe endlessly - hard limit to 10 re-subscribes
        if (starts.get() < 10) {
            Mono.delay(Duration.ofSeconds(60L))
                .doOnNext(i -> start())
                .subscribe();
        } else {
            LOGGER.error("Gave up restarting, because of more than 10 restarts of main kafka consuming chain");
        }
    }

    protected Flux<ReceiverRecord<String, String>> receiveWithRetry() {
        return reactiveKafkaConsumerTemplate.receive()
            .retryWhen(applicationProperties.getConsumer().getRetrySpecification().toRetry())
            .doOnNext(this::logReceived);
    }

    protected Flux<ReceiverRecord<String, String>> receive() {
        return reactiveKafkaConsumerTemplate.receive()
            .doOnNext(this::logReceived);
    }

    protected Mono<SenderResult<ReceiverOffset>> send(SenderRecord<String, String, ReceiverOffset> senderRecord) {
        return reactiveKafkaProducerTemplate.send(senderRecord)
            .doOnNext(this::logSent);
    }

    // No more used - too many differences in reactive flow
    /*
    protected void ackOrCommit(SenderResult<ReceiverOffset> senderResult) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
    }
    */

    protected Mono<Void> commit(SenderResult<ReceiverOffset> senderResult) {

        final int partition = senderResult.correlationMetadata().topicPartition().partition();
        final long offset = senderResult.correlationMetadata().offset();
        counterService.logRateCommitted(partition, offset);
        return senderResult.correlationMetadata().commit();
    }

    private void logReceived(ReceiverRecord<String,String> receiverRecord) {

        counterService.logRateReceived(receiverRecord.partition(), receiverRecord.offset());
    }

    private void logSent(SenderResult<ReceiverOffset> senderResult) {

        final int partition = senderResult.correlationMetadata().topicPartition().partition();
        final long offset = senderResult.correlationMetadata().offset();
        counterService.logRateSent(partition, offset);
    }
}
