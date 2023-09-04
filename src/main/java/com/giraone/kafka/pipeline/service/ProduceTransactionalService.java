package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.SenderRecord;

@Service
public class ProduceTransactionalService extends AbstractProduceService {

    public ProduceTransactionalService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        LOGGER.info("STARTING to produce {} events using ProduceTransactionalService.", maxNumberOfEvents);
        final long start = System.currentTimeMillis();
        source(applicationProperties.getProduceInterval(), maxNumberOfEvents)
            .flatMap(tuple -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                return reactiveKafkaProducerTemplate.sendTransactionally(SenderRecord.create(producerRecord, tuple.getT1()));
            })
            .doOnNext(senderResult -> counterService.logRateSent(senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()))
            .doOnError(e -> counterService.logError("ProduceService failed!", e))
            .subscribe(null, counterService::logMainLoopError, () -> {
                LOGGER.info("Finished producing {} events after {} seconds", maxNumberOfEvents, (System.currentTimeMillis() - start) / 1000L);
                schedulerForKafkaProduce.disposeGracefully().block();
            });
        counterService.logMainLoopStarted();
    }
}