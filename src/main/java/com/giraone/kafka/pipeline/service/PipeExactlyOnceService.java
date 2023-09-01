package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.TransactionManager;

@Service
public class PipeExactlyOnceService extends AbstractPipeService {

    public PipeExactlyOnceService(
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

        // TODO: unclear, if this is correct
        final TransactionManager transactionManager = reactiveKafkaProducerTemplate.transactionManager();
        reactiveKafkaConsumerTemplate.receiveExactlyOnce(transactionManager)
            .concatMap(consumerRecordFlux -> consumerRecordFlux
                .doOnNext(consumerRecord -> counterService.logRateReceive(consumerRecord.partition(), consumerRecord.offset()))
                .concatMap(consumerRecord -> reactiveKafkaProducerTemplate.send(process(consumerRecord))))
                .concatWith(transactionManager.commit())
            .onErrorResume(e -> transactionManager.abort().then(Mono.error(e)))
            .subscribe();
    }
}