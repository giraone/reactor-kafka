package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;

@Service
public class PipeSendReceiveService extends AbstractPipeService {

    public PipeSendReceiveService(
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

        LOGGER.info("Assembly of {}", this.getClass().getSimpleName());
        reactiveKafkaProducerTemplate
            .send(
                reactiveKafkaConsumerTemplate.receive()
                    // this is the Kafka consume retry
                    .retryWhen(retry)
                    // log the received event
                    .doOnNext(receiverRecord -> counterService.logRateReceived(receiverRecord.partition(), receiverRecord.offset()))
                    // perform the pipe task
                    .flatMap(this::process, applicationProperties.getConsumer().getConcurrency(), 1)
            )
            // commit every processed record
            .flatMap(this::commit, applicationProperties.getConsumer().getConcurrency(), 1)
            // log any error
            .doOnError(e -> counterService.logError("PipeSendReceiveService failed!", e))
            // subscription main loop - restart on unhandled errors
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted(getClass().getSimpleName());
    }
}