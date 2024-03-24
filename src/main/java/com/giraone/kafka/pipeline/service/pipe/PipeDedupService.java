package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.CounterService;
import com.giraone.kafka.pipeline.util.lookup.LookupService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.SenderRecord;

@Service
public class PipeDedupService extends AbstractPipeService {

    private final LookupService lookupService;

    public PipeDedupService(
        ApplicationProperties applicationProperties,
        CounterService counterService,
        ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate,
        ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate,
        LookupService lookupService
    ) {
        super(applicationProperties, counterService, reactiveKafkaProducerTemplate, reactiveKafkaConsumerTemplate);
        this.lookupService = lookupService;
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void start() {

        LOGGER.info("Assembly of {}", this.getClass().getSimpleName());
        subscription = this.receiveWithRetry()
            // perform processing on another scheduler
            .publishOn(buildScheduler())
            .doOnNext(this::logReceived)
            // perform the pipe task: check for duplicates, send non-duplicates
            .flatMap(this::checkAndSendIfUnique, applicationProperties.getConsumer().getConcurrency(), 1)
            // commit every processed record
            .flatMap(this::commit, applicationProperties.getConsumer().getConcurrency(), 1)
            // log any error
            .doOnError(e -> counterService.logError("PipeDedupService failed!", e))
            // subscription main loop - restart on unhandled errors
            .subscribe(null, this::restartMainLoopOnError);
        counterService.logMainLoopStarted(getClass().getSimpleName());
    }

    protected Mono<ReceiverRecord<String, String>> checkAndSendIfUnique(ReceiverRecord<String, String> inputRecord) {

        final String key = inputRecord.key();
        LOGGER.debug("Received key {}", key);
        return lookupService.lookup(key)
            .flatMap(foundValue -> {
                if (foundValue != null) {
                    // Log and document metric
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Duplicate key \"{}\" detected, that was {} ms old", key, (System.currentTimeMillis() - Long.parseLong(foundValue)));
                    }
                    counterService.logRateDuplicates(inputRecord.partition(), inputRecord.offset());
                    // return the received record, that is used for the commit
                    return Mono.just(inputRecord);
                } else {
                    // We use timestamp here (because of log how old the entry was) - but true/false maybe OK also
                    lookupService.put(key, Long.toString(System.currentTimeMillis()));
                    // send unique result to target topic
                    return send(SenderRecord.create(new ProducerRecord<>(getTopicOutput(), key, inputRecord.value()), inputRecord.receiverOffset()))
                        // return the received record, that is used for the commit
                        .map(senderResult -> inputRecord);
                }
            });
    }

    @EventListener
    public void onApplicationCloseEvent(ContextClosedEvent contextClosedEvent) {
        super.onApplicationCloseEvent(contextClosedEvent);
    }
}