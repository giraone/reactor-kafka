package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;

@Service
public class PipePartitionedService extends AbstractPipeService {

    public PipePartitionedService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate

    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() { // receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())

        LOGGER.info("Assembly of {}", this.getClass().getSimpleName());
        this.receiveWithRetry()
            // group by partition to guarantee ordering
            .groupBy(receiverRecord -> receiverRecord.receiverOffset().topicPartition())
            .flatMap(partitionFlux ->
                // See https://projectreactor.io/docs/kafka/release/reference/ - chapter 5.12
                partitionFlux.publishOn(buildScheduler())
                    // perform the pipe task - TODO: is flatMap an option?
                    .concatMap(this::process)
                    // send result to target topic
                    .concatMap(this::send)
                    // sample() disabled, because it caused "Can't signal value due to lack of requests" errors.
                    // .sample(applicationProperties.getConsumer().getCommitInterval()) // Commit periodically
                    // commit every processed record
                    .concatMap(this::commit)
            )
            // log any error
            .doOnError(e -> counterService.logError("PipePartitionedService failed!", e))
            // subscription main loop - restart on unhandled errors
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted(getClass().getSimpleName());
    }
}