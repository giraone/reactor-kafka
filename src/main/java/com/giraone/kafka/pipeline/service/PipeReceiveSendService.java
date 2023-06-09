package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.sender.SenderResult;

@Service
public class PipeReceiveSendService extends AbstractPipeService {

    public PipeReceiveSendService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        CounterService counterService
    ) {
        super(applicationProperties, reactiveKafkaConsumerTemplate, reactiveKafkaProducerTemplate, counterService);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> counterService.logRate("RECV", receiverRecord.partition(), receiverRecord.offset()))
            .flatMap(receiverRecord -> reactiveKafkaProducerTemplate.send(transformToSenderRecord(receiverRecord, topic2)))
            .doOnNext(this::ack)
            .subscribe();
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
    }
}