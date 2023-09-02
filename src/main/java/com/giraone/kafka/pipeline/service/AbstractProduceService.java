package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
        this.maxNumberOfEvents = applicationProperties.getProducer().getMaxNumberOfEvents();
        this.topicOutput = applicationProperties.getTopicA();
    }
}