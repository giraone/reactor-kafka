package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractProduceService extends AbstractService {

    // One single thread is enough to generate numbers and System.currentTimeMillis() tupels
    protected static final Scheduler schedulerForProduce = Schedulers.newParallel("generate", 1, true);

    protected final ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate;
    protected final String topicOutput;
    protected final int maxNumberOfEvents;

    public AbstractProduceService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate
    ) {
        super(applicationProperties, counterService);
        this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
        this.maxNumberOfEvents = applicationProperties.getProducerVariables().getMaxNumberOfEvents();
        this.topicOutput = applicationProperties.getTopicA();
    }

    protected Flux<Tuple2<String, String>> source(Duration delay, int limit) {

        final AtomicInteger counter = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
        return Flux.interval(delay, schedulerForProduce)
            .take(limit)
            .map(ignored -> counter.getAndIncrement())
            .map(nr -> Tuples.of(Long.toString(nr), Long.toString(System.currentTimeMillis())))
            .doOnNext(t -> counterService.logRateProduced());
    }
}