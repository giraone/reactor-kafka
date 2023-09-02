package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.sender.SenderResult;

@Service
public class PipeReceiveSendService extends PipeService {

    public PipeReceiveSendService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> counterService.logRateReceive(receiverRecord.partition(), receiverRecord.offset()))
            .flatMap(receiverRecord -> reactiveKafkaProducerTemplate.send(process(receiverRecord)), applicationProperties.getConsumer().getThreads(), 1)
            .doOnNext(this::ack)
            .doOnError(e -> counterService.logError("PipeReceiveSendService failed!", e))
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
    }
}