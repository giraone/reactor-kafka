package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.sender.SenderResult;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PipeService extends AbstractPipeService {

    protected final AtomicInteger starts = new AtomicInteger();

    protected final Scheduler scheduler;
    protected final String topicInput;
    protected final String topicOutput;

    public PipeService(ApplicationProperties applicationProperties,
                       CounterService counterService,
                       ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
                       ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
        this.scheduler = applicationProperties.getConsumer().buildScheduler();
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

    protected void restartMainLoopOnError(Throwable throwable) {
        counterService.logMainLoopError(throwable);
        // We do not re-subscribe endlessly - hard limit to 10 re-subscribes
        if (starts.get() < 10) {
            Mono.delay(Duration.ofSeconds(60L))
                .doOnNext(i -> start())
                .subscribe();
        } else {
            LOGGER.error("Gave up restarting, because of more than 10 restarts of main kafka consuming chain");
        }
    }

    // No more used - too many differences in reactive flow
    /*
    protected void ackOrCommit(SenderResult<ReceiverOffset> senderResult) {

        if (applicationProperties.getConsumer().isAutoCommit()) {
            senderResult.correlationMetadata().acknowledge();
        } else {
            senderResult.correlationMetadata().commit().block();
        }
    }
    */
    protected Mono<Void> commit(SenderResult<ReceiverOffset> senderResult) {

        final int partition = senderResult.correlationMetadata().topicPartition().partition();
        final long offset = senderResult.correlationMetadata().offset();
        return senderResult.correlationMetadata().commit()
            .doOnNext(ignored -> counterService.logRateCommitted(partition, offset));
    }

    protected void logSent(SenderResult<ReceiverOffset> senderResult) {

        final int partition = senderResult.correlationMetadata().topicPartition().partition();
        final long offset = senderResult.correlationMetadata().offset();
        counterService.logRateSent(partition, offset);
    }
}
