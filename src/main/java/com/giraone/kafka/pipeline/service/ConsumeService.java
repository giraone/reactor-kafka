package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
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
            // perform processing on another scheduler
            .publishOn(buildScheduler())
            .doOnNext(this::consume)
            .doOnNext(this::commit)
            .doOnError(e -> counterService.logError("ConsumeService failed!", e))
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }

    protected Scheduler buildScheduler() {
        return applicationProperties.getConsumer().buildScheduler();
    }

    protected void consume(ReceiverRecord<String, String> receiverRecord) {

        counterService.logRateReceived(receiverRecord.partition(), receiverRecord.offset());
    }

    protected void commit(ReceiverRecord<String, String> receiverRecord) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            receiverRecord.receiverOffset().acknowledge();
        } else {
            receiverRecord.receiverOffset().commit().block();
        }
        counterService.logRateCommitted(receiverRecord.partition(), receiverRecord.offset());
    }
}