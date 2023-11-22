package com.giraone.kafka.pipeline.service.consume;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.AbstractService;
import com.giraone.kafka.pipeline.service.CounterService;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
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
            .map(this::commit)
            .doOnError(e -> counterService.logError("ConsumeService failed!", e))
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }


    protected void consume(ReceiverRecord<String, String> receiverRecord) {
        // Simple consume, that only logs
        counterService.logRateReceived(receiverRecord.partition(), receiverRecord.offset());
    }
}