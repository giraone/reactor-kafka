package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;

import java.util.HashSet;
import java.util.Set;

@Service
public class PipeDedupService extends AbstractPipeService {

    private final Set<String> lastRecords = new HashSet<>();

    public PipeDedupService(
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

        subscription = this.receiveWithRetry()
            // perform processing on another scheduler
            .publishOn(buildScheduler())
            .doOnNext(this::logReceived)
            // perform the pipe task: check for duplicates, send non-duplicates and commit in any case
            .flatMap(this::check, applicationProperties.getConsumer().getConcurrency(), 1)
            // log any error
            .doOnError(e -> counterService.logError("PipeDedupService failed!", e))
            // subscription main loop - restart on unhandled errors
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted();
    }

    protected Mono<ReceiverRecord<String, String>> check(ReceiverRecord<String, String> inputRecord) {

        final String key = inputRecord.key();
        if (lastRecords.contains(key)) {
            LOGGER.info("Duplicate key {} detected", key);
            counterService.logRateDuplicates(inputRecord.partition(), inputRecord.offset());
            return commit(inputRecord);
        } else {
            lastRecords.add(key);
            // send unique result to target topic
            return send(SenderRecord.create(new ProducerRecord<>(getTopicOutput(), key, inputRecord.value()), inputRecord.receiverOffset()))
                .map(senderResult -> inputRecord)
                .flatMap(this::commit);
        }
    }

    @EventListener
    public void onApplicationCloseEvent(ContextClosedEvent contextClosedEvent) {
        super.onApplicationCloseEvent(contextClosedEvent);
    }
}