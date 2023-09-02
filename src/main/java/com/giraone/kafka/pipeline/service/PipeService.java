package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public abstract class PipeService extends AbstractPipeService {

    protected final Scheduler scheduler;
    protected final String topicInput;
    protected final String topicOutput;

    public PipeService(ApplicationProperties applicationProperties,
                       CounterService counterService,
                       ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
                       ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
        this.scheduler = Schedulers.newParallel("worker", applicationProperties.getConsumer().getThreads());
        this.topicInput = applicationProperties.getTopicA();
        this.topicOutput = applicationProperties.getTopicB();
    }

    @Override
    protected String getTopicInput() {
        return topicInput;
    }

    @Override
    protected String getTopicOutput() {
        return topicOutput;
    }
}
