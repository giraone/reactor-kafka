package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
public class PipePartitionedService extends AbstractPipeService {

    private final Scheduler scheduler;

    public PipePartitionedService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        CounterService counterService
    ) {
        super(applicationProperties, reactiveKafkaConsumerTemplate, reactiveKafkaProducerTemplate, counterService);
        this.scheduler = Schedulers.newParallel("worker",
            applicationProperties.getConsumer().getThreads(), true);
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
                    .doOnNext(receiverRecord -> counterService.logRate("RECV", receiverRecord.partition(), receiverRecord.offset()))
                    .flatMap(receiverRecord -> reactiveKafkaProducerTemplate.send(transformToSenderRecord(receiverRecord, topic2)))
                    .sample(applicationProperties.getConsumer().getCommitInterval()) // Commit periodically
                    .concatMap(senderResult -> senderResult.correlationMetadata().commit()
                        .doOnNext(unused -> counterService.logRate("COMT",
                            senderResult.correlationMetadata().topicPartition().partition(), senderResult.correlationMetadata().offset())))
            )
            .subscribe();
    }
}