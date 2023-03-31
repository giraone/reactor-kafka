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
    public void start() { // receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())

        TransactionManager transactionManager = null;

        reactiveKafkaConsumerTemplate.receiveExactlyOnce(transactionManager)
            .retryWhen(retry)
            .concatMap(consumerRecordFlux -> reactiveKafkaProducerTemplate.send(
                    consumerRecordFlux.map(consumerRecord -> transformToSenderRecord(consumerRecord, topic2))
                )
                .concatWith(transactionManager.commit()))
            .onErrorResume(e -> transactionManager.abort().then(Mono.error(e)))
            .subscribe();
    }
}