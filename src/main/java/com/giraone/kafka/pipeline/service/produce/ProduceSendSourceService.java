package com.giraone.kafka.pipeline.service.produce;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.SenderRecord;

@Service
public class ProduceSendSourceService extends AbstractProduceService {

    public ProduceSendSourceService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        LOGGER.info("STARTING to produce {} events using ProduceSendSourceService.", maxNumberOfEvents);
        final long start = System.currentTimeMillis();
        this.send(source(applicationProperties.getProduceInterval(), maxNumberOfEvents)
                .map(tuple -> {
                    final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                    return SenderRecord.create(producerRecord, tuple.getT1());
                })
            )
            // A scheduler is needed - a single or parallel(1) is OK
            .publishOn(schedulerForKafkaProduce)
            .doOnError(e -> counterService.logError("ProduceService failed!", e))
            .subscribe(null, counterService::logMainLoopError, () -> {
                LOGGER.info("Finished producing {} events to {} after {} seconds", maxNumberOfEvents, topicOutput,
                    (System.currentTimeMillis() - start) / 1000L);
                disposeGracefully();
            });
        counterService.logMainLoopStarted();
    }
}