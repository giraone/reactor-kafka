package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;

import java.util.Locale;

public abstract class AbstractPipeService extends AbstractService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractPipeService.class);

    protected final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;
    protected final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;

    protected final Retry retry;

    public AbstractPipeService(ApplicationProperties applicationProperties,
                               ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
                               ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
                               CounterService counterService) {
        super(applicationProperties, counterService);
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;

        this.retry = applicationProperties.getConsumerProperties().getRetrySpecification().toRetry();
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
     * The core pipeline task - here a simple convert toUpperCase
     */
    protected String transform(String input) {
        if (applicationProperties.getTransformInterval() != null) {
            try {
                Thread.sleep(applicationProperties.getTransformInterval().toMillis());
                counterService.logRate("TASK");
            } catch (InterruptedException e) {
                LOGGER.error("Transform interrupted!");
            }
        }
        return input.toUpperCase(Locale.ROOT);
    }

    protected ProducerRecord<String, String> transformToProducerRecord(ConsumerRecord<String, String> consumerRecord, String topicOutput) {
        final ProducerRecord<String, String> ret = new ProducerRecord<>(topicOutput, consumerRecord.key(), transform(consumerRecord.value()));
        LOGGER.debug("<<< {}={}", ret.key(), ret.value());
        return ret;
    }

    protected SenderRecord<String, String, String> transformToSenderRecord(ConsumerRecord<String, String> consumerRecord, String topicOutput) {
        final ProducerRecord<String, String> producerRecord = transformToProducerRecord(consumerRecord, topicOutput);
        return SenderRecord.create(producerRecord, null);
    }

    protected SenderRecord<String, String, ReceiverOffset> transformToSenderRecord(ReceiverRecord<String, String> receiverRecord, String topicOutput) {
        final ProducerRecord<String, String> producerRecord = transformToProducerRecord(receiverRecord, topicOutput);
        // pass receiverOffset as correlation metadata to commit on send
        return SenderRecord.create(producerRecord, receiverRecord.receiverOffset());
    }
}
