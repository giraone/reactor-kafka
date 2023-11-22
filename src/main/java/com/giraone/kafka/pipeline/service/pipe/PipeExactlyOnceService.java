package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.TransactionManager;

@Transactional
@Service
public class PipeExactlyOnceService extends AbstractPipeService {

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
                .doOnNext(consumerRecord -> counterService.logRateReceived(consumerRecord.partition(), consumerRecord.offset()))
                .concatMap(consumerRecord -> reactiveKafkaProducerTemplate.send(process(consumerRecord))))
            .concatWith(transactionManager.commit())
            .onErrorResume(e -> {
                counterService.logError("PipeExactlyOnceService failed!", e);
                return transactionManager.abort().then(Mono.error(e));
            })
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted();
    }
}