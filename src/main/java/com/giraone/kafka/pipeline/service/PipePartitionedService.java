package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;

@Service
public class PipePartitionedService extends PipeService {

    public PipePartitionedService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate

    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() { // receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())

        reactiveKafkaConsumerTemplate.receive()
            .retryWhen(retry)
            // Concurrent processing with partition-based ordering
            .groupBy(receiverRecord -> receiverRecord.receiverOffset().topicPartition())
            .flatMap(partitionFlux ->
                partitionFlux.publishOn(scheduler)
                    .doOnNext(receiverRecord -> counterService.logRateReceive(receiverRecord.partition(), receiverRecord.offset()))
                    .flatMap(this::process, 1, 1)
                    .sample(applicationProperties.getConsumer().getCommitInterval()) // Commit periodically
                    .concatMap(senderResult -> senderResult.correlationMetadata().commit()
                        .doOnNext(unused -> counterService.logRateCommit(
                            senderResult.correlationMetadata().topicPartition().partition(),
                            senderResult.correlationMetadata().offset())))
            )
            .doOnError(e -> counterService.logError("PipePartitionedService failed!", e))
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }
}