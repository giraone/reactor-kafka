package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.SenderRecord;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;

@Service
public class ProduceService extends AbstractService {

    // One single thread is enough to generate numbers and System.currentTimeMillis() tupels
    private static final Scheduler schedulerForProduce = Schedulers.newParallel("generate", 1, true);

    private final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    private final String topicOutput;

    public ProduceService(
        ApplicationProperties applicationProperties,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        CounterService counterService
    ) {
        super(applicationProperties, counterService);
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.topicOutput = applicationProperties.getTopicOutput();
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        reactiveKafkaProducerTemplate.send(source(applicationProperties.getProduceInterval(), Integer.MAX_VALUE)
                .map(tuple -> {
                    final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                    return SenderRecord.create(producerRecord, tuple.getT1());
                })
            )
            .doOnNext(senderResult -> counterService.logRate("SEND", senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()))
            .doOnError(e -> LOGGER.error("Send failed", e))
            .subscribe();
    }

    protected Flux<Tuple2<String, String>> source(Duration delay, int limit) {

        final int s = (int) (System.currentTimeMillis() / 1000L);
        // Emit integers starting with actual epoch seconds
        return Flux.range(s, limit - s)
            .delayElements(delay, schedulerForProduce)
            .map(nr -> Tuples.of(Long.toString(nr), Long.toString(System.currentTimeMillis())))
            .doOnNext(t -> counterService.logRate("PROD"));
    }
}