package com.giraone.kafka.pipeline.service.produce;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.AbstractService;
import com.giraone.kafka.pipeline.service.CounterService;
import org.reactivestreams.Publisher;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractProduceService extends AbstractService {

    // One single thread is enough to generate numbers and System.currentTimeMillis() tupels
    protected static final Scheduler schedulerForGenerateNumbers = Schedulers.newSingle("generateNumberScheduler", false);
    protected static final Scheduler schedulerForKafkaProduce = Schedulers.newSingle("producerScheduler", false);

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
        return sourceHot(delay, limit);
    }

    protected Flux<Tuple2<String, String>> sourceHot(Duration delay, int limit) {

        final AtomicInteger counter = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
        return Flux.range(0, limit)
            .delayElements(delay, schedulerForGenerateNumbers)
            .map(ignored -> counter.getAndIncrement())
            .map(nr -> Tuples.of(Long.toString(nr), buildContent()))
            .doOnNext(t -> counterService.logRateProduced());
    }

    protected Flux<Tuple2<String, String>> sourceCold(Duration delay, int limit) {

        final AtomicInteger counter = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
        return Flux.interval(delay, schedulerForGenerateNumbers)
            .take(limit)
            .map(ignored -> counter.getAndIncrement())
            .map(nr -> Tuples.of(Long.toString(nr), buildContent()))
            .doOnNext(t -> counterService.logRateProduced());
    }

    protected Flux<Tuple2<String, String>> sourceHotWithDuplicates(Duration delay, int limit, float duplicatePercentage) {
        final Random random = new Random();
        final AtomicInteger counter = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
        return Flux.range(0, limit)
            .delayElements(delay, schedulerForGenerateNumbers)
            .map(ignored -> {
                if (random.nextFloat() >= duplicatePercentage) {
                    LOGGER.info("Duplicate key {} produced", counter.get());
                    return counter.get();
                } else {
                    return counter.getAndIncrement();
                }
            })
            .map(nr -> Tuples.of(Long.toString(nr), buildContent()))
            .doOnNext(t -> counterService.logRateProduced());
    }

    protected Mono<SenderResult<String>> send(SenderRecord<String, String, String> senderRecord) {

        return reactiveKafkaProducerTemplate.send(senderRecord)
            .doOnNext(senderResult -> counterService.logRateSent(senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()));
    }

    protected Flux<SenderResult<String>> send(Publisher<? extends SenderRecord<String, String, String>> senderRecords) {
        return reactiveKafkaProducerTemplate.send(senderRecords)
            .doOnNext(senderResult -> counterService.logRateSent(senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset()));
    }

    protected void disposeGracefully() {
        // TODO: leads to problems in tests
        schedulerForKafkaProduce.disposeGracefully().timeout(Duration.ofSeconds(5L)).subscribe(unused -> schedulerForKafkaProduce.dispose());
    }

    private String buildContent() {
        return String.valueOf((char) (65 + System.currentTimeMillis() % 26)).repeat(10);
    }
}