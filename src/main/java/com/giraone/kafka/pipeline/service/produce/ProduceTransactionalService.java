package com.giraone.kafka.pipeline.service.produce;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;
import reactor.util.function.Tuple2;

import java.util.List;

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
            .buffer(2) // collect a list of n events and send these n events transactionally
            .map(list -> {
                reactiveKafkaProducerTemplate.transactionManager().begin()
                    .then(send(list))
                    .then(reactiveKafkaProducerTemplate.transactionManager().commit());
                return list;
            })
            .doOnError(e -> counterService.logError("ProduceService failed!", e))
            .subscribe(null, counterService::logMainLoopError, () -> {
                LOGGER.info("Finished producing {} events to {} after {} seconds", maxNumberOfEvents, topicOutput,
                    (System.currentTimeMillis() - start) / 1000L);
                disposeGracefully();
            });
        counterService.logMainLoopStarted();
    }

    private Mono<SenderResult<Void>> send(Tuple2<String,String> tuple) {
        final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
        return reactiveKafkaProducerTemplate.send(producerRecord)
            .doOnNext(senderResult -> counterService.logRateSent(senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()));
    }

    private Mono<SenderResult<Void>> send(List<Tuple2<String,String>> listOfTuple) {
        return listOfTuple.stream().map(this::send).reduce((all, element) -> element).orElse(Mono.empty());
    }
}