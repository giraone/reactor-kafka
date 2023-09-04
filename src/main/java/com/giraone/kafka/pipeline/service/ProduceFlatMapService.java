package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.SenderRecord;

@Service
public class ProduceFlatMapService extends AbstractProduceService {

    public ProduceFlatMapService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        LOGGER.info("STARTING to produce {} events using ProduceFlatMapService.", maxNumberOfEvents);
        source(applicationProperties.getProduceInterval(), maxNumberOfEvents)
            // A scheduler is needed - a single or parallel(1) is OK
            .publishOn(schedulerForKafkaProduce)
            .flatMap(tuple -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                final SenderRecord<String, String, String> senderRecord = SenderRecord.create(producerRecord, tuple.getT1());
                return this.send(senderRecord);
            })
            .doOnError(e -> counterService.logError("ProduceFlatMapService failed!", e))
            .subscribe(null, counterService::logMainLoopError, () -> schedulerForKafkaProduce.disposeGracefully().block());
        counterService.logMainLoopStarted();
    }
}