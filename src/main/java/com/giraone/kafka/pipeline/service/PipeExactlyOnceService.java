package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.TransactionManager;

@Service
public class PipeExactlyOnceService extends PipeService {

    public PipeExactlyOnceService(
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

        // TODO: unclear, if this is correct
        final TransactionManager transactionManager = reactiveKafkaProducerTemplate.transactionManager();
        reactiveKafkaConsumerTemplate.receiveExactlyOnce(transactionManager)
            .concatMap(consumerRecordFlux -> consumerRecordFlux
                .doOnNext(consumerRecord -> counterService.logRateReceive(consumerRecord.partition(), consumerRecord.offset()))
                .concatMap(consumerRecord -> reactiveKafkaProducerTemplate.send(process(consumerRecord))))
            .concatWith(transactionManager.commit())
            .onErrorResume(e -> {
                counterService.logError("PipeExactlyOnceService failed!", e);
                return transactionManager.abort().then(Mono.error(e));
            })
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }
}