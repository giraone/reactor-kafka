package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverRecord;

@Service
public class ConsumeService extends AbstractService {

    private final ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate;

    public ConsumeService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        CounterService counterService
    ) {
        super(applicationProperties, counterService);
        this.reactiveKafkaConsumerTemplate = reactiveKafkaConsumerTemplate;
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(this::consume)
            .doOnNext(this::ack)
            .subscribe();
    }

    protected void consume(ReceiverRecord<String, String> receiverRecord) {

        counterService.logRate("RECV", receiverRecord.partition(), receiverRecord.offset());
    }

    protected void ack(ReceiverRecord<String, String> receiverRecord) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            receiverRecord.receiverOffset().acknowledge();
        } else {
            receiverRecord.receiverOffset().commit().block();
        }
        counterService.logRate("ACKN", receiverRecord.partition(), receiverRecord.offset());
    }
}