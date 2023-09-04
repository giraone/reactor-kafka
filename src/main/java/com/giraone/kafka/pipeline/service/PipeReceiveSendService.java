package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;

@Service
public class PipeReceiveSendService extends AbstractPipeService {

    public PipeReceiveSendService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        this.receiveWithRetry()
            // perform processing on another scheduler
            .publishOn(buildScheduler())
            // perform the pipe task
            .flatMap(this::process, applicationProperties.getConsumer().getConcurrency(), 1)
            // send result to target topic
            .flatMap(this::send, applicationProperties.getConsumer().getConcurrency(), 1)
            // commit every processed record
            .flatMap(this::commit, applicationProperties.getConsumer().getConcurrency(), 1)
            // log any error
            .doOnError(e -> counterService.logError("PipeReceiveSendService failed!", e))
            // subscription main loop - restart on unhandled errors
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted();
    }
}