package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.SenderRecord;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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
        reactiveKafkaProducerTemplate.send(source(applicationProperties.getProduceInterval(), maxNumberOfEvents)
                .map(tuple -> {
                    final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                    return SenderRecord.create(producerRecord, tuple.getT1());
                })
            )
            .doOnNext(senderResult -> counterService.logRateSent(senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()))
            .doOnError(e -> counterService.logError("ProduceService failed!", e))
            .subscribe(null, counterService::logMainLoopError);
        counterService.logMainLoopStarted();
    }

    protected Flux<Tuple2<String, String>> source(Duration delay, int limit) {

        final AtomicInteger counter = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
        return Flux.interval(delay, schedulerForProduce)
            .take(limit)
            .map(ignored -> counter.getAndIncrement())
            .map(nr -> Tuples.of(Long.toString(nr), Long.toString(System.currentTimeMillis())))
            .doOnNext(t -> counterService.logRateProduced())
            ;
    }
}