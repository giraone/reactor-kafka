package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.ContextClosedEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractService implements CommandLineRunner {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractService.class);

    protected final AtomicInteger starts = new AtomicInteger();
    // used to save the subscription of the main consumer loop, so we can dispose on shutdown, to stop consuming during shutdown
    protected Disposable subscription;

    protected final ApplicationProperties applicationProperties;
    protected final CounterService counterService;

    public AbstractService(ApplicationProperties applicationProperties,
                           CounterService counterService) {
        this.applicationProperties = applicationProperties;
        this.counterService = counterService;
    }

    protected abstract void start();

    @Override
    public void run(String... args) {

        if (!(applicationProperties.getMode() + "Service").equalsIgnoreCase(this.getClass().getSimpleName())) {
            return;
        }
        LOGGER.info("STARTING {}", this.getClass().getSimpleName());
        this.start();
    }

    protected Scheduler buildScheduler() {
        return applicationProperties.getConsumer().buildScheduler();
    }

    protected Mono<ReceiverRecord<String, String>> commit(ReceiverRecord<String, String> receiverRecord) {

        return receiverRecord.receiverOffset().commit()
            .map(unused -> {
                counterService.logRateCommitted(receiverRecord.partition(), receiverRecord.offset());
                return receiverRecord;
            });
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

    protected void onApplicationCloseEvent(ContextClosedEvent ignoredEvent) {
        LOGGER.info("Got shutdown signal, disposing main consumer loop subscription...");
        if (subscription != null) {
            subscription.dispose();
        }
    }

    protected void logReceived(ReceiverRecord<String,String> receiverRecord) {
        counterService.logRateReceived(receiverRecord.partition(), receiverRecord.offset());
    }
}
