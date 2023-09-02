package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.sender.SenderResult;

@Service
public class PipeSendReceiveService extends AbstractPipeService {

    public PipeSendReceiveService(
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

        reactiveKafkaProducerTemplate
            .send(
                reactiveKafkaConsumerTemplate.receive()
                    .retryWhen(retry)
                    .doOnNext(receiverRecord -> counterService.logRateReceive(receiverRecord.partition(), receiverRecord.offset()))
                    .flatMap(this::process)
            )
            .doOnNext(this::ack)
            .doOnError(e -> counterService.logError("PipeSendReceiveService failed!", e))
            .subscribe(null, counterService::logPipelineStoppedOnError);
    }

    private void ack(SenderResult<ReceiverOffset> senderResult) {


        if (applicationProperties.getConsumer().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
    }
}