package com.giraone.kafka.pipeline.service.produce;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.SenderRecord;

/**
 * A producer which also creates duplicate events, that can be detected by
 * the {@link com.giraone.kafka.pipeline.service.pipe.PipeDedupService}.
 */
@Service
public class ProduceWithDuplicatesService extends AbstractProduceService {

    public ProduceWithDuplicatesService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        final long start = System.currentTimeMillis();
        sourceHotWithDuplicates(applicationProperties.getProduceInterval(),
            maxNumberOfEvents, applicationProperties.getProducerVariables().getDuplicatePercentage() / 100.0f)
            // A scheduler is needed - a single or parallel(1) is OK
            .publishOn(schedulerForKafkaProduce)
            .flatMap(tuple -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                final SenderRecord<String, String, String> senderRecord = SenderRecord.create(producerRecord, tuple.getT1());
                return this.send(senderRecord);
            })
            .doOnError(e -> counterService.logError("ProduceWithDuplicatesService failed!", e))
            .subscribe(null, counterService::logMainLoopError, () -> {
                LOGGER.info("Finished producing {} events to {} after {} seconds", maxNumberOfEvents, topicOutput,
                    (System.currentTimeMillis() - start) / 1000L);
                disposeGracefully();
            });
        counterService.logMainLoopStarted(getClass().getSimpleName());
    }
}