package com.giraone.kafka.pipeline.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CounterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CounterService.class);
    private static final long LOG_PERIOD_MS = 1000L;

    // per metric, per partition
    private final Map<String, Map<Integer, BeforeAndNowCounter>> counterPerMetric = new HashMap<>();
    private final Map<String, BeforeAndNowCounter> totalCounterPerMetric = new HashMap<>();

    private final Counter counterProduced;
    private final Counter counterSent;

    private final Counter counterReceived;
    private final Counter counterProcessed;
    private final Counter counterCommitted;

    private final Counter counterError;
    private final Counter counterMainLoopStarted;
    private final Counter counterMainLoopStopped;

    public CounterService(MeterRegistry registry) {

        this.counterProduced = registry.counter("pipeline.produced");
        this.counterSent = registry.counter("pipeline.sent");

        this.counterReceived = registry.counter("pipeline.received");
        this.counterProcessed = registry.counter("pipeline.processed");
        this.counterCommitted = registry.counter("pipeline.committed");

        this.counterError = registry.counter("pipeline.error");
        this.counterMainLoopStarted = registry.counter("pipeline.loop.started");
        this.counterMainLoopStopped = registry.counter("pipeline.loop.stopped");
    }

    public void logRateSent(int partition, long offset) {
        logRateInternal("SENT", partition, offset);
        counterSent.increment();
    }

    public void logRateReceived(int partition, long offset) {
        logRateInternal("RECV", partition, offset);
        counterReceived.increment();
    }

    public void logRateCommitted(int partition, long offset) {
        logRateInternal("CMMT", partition, offset);
        counterCommitted.increment();
    }

    public void logRateProduced() {
        logRateInternal("PROD", -1, -1);
        counterProduced.increment();
    }

    public void logRateProcessed() {
        logRateInternal("TASK", -1, -1);
        counterProcessed.increment();
    }

    public void logError(String errorMessage, Throwable throwable) {
        LOGGER.error("ERROR! {} ", errorMessage, throwable);
        counterError.increment();
    }

    public void logMainLoopStarted() {
        LOGGER.info("Main loop started!");
        counterMainLoopStarted.increment();
    }

    public void logMainLoopError(Throwable throwable) {
        LOGGER.error("Main loop error! ", throwable);
        counterMainLoopStopped.increment();
    }

    private void logRateInternal(String metric, int partition, long offset) {

        final long now = System.currentTimeMillis();

        BeforeAndNowCounter counterTotal = totalCounterPerMetric.get(metric);
        if (counterTotal == null) {
            counterTotal = new BeforeAndNowCounter();
            totalCounterPerMetric.put(metric, counterTotal);
        }
        counterTotal.value++;

        if (partition >= 0) {
            final Map<Integer, BeforeAndNowCounter> countersPerPartition = counterPerMetric.computeIfAbsent(metric, k -> new HashMap<>());
            BeforeAndNowCounter counterForPartition = countersPerPartition.get(partition);
            if (counterForPartition == null) {
                counterForPartition = new BeforeAndNowCounter();
                countersPerPartition.put(partition, counterForPartition);
            }
            counterForPartition.value++;

            if ((now - counterForPartition.lastLog) > LOG_PERIOD_MS) {
                final long partitionRate = counterForPartition.value * 1000L / (now - counterForPartition.start);
                final long totalRate = counterTotal.value * 1000L / (now - counterTotal.start);
                LOGGER.info("{}/{}: ops/p={} ops={} offset={} total/p={} total={}",
                    metric, partition, partitionRate, totalRate, offset, counterForPartition.value, counterTotal.value);
                counterForPartition.lastLog = now;
                counterTotal.lastLog = now;
            }
        } else {
            if ((now - counterTotal.lastLog) > LOG_PERIOD_MS) {
                final long totalRate = counterTotal.value * 1000L / (now - counterTotal.start);
                LOGGER.info("{}/*: ops={} total={}",
                    metric, totalRate, counterTotal.value);
                counterTotal.lastLog = now;
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class BeforeAndNowCounter {
        private long value = 0L;
        private long start = System.currentTimeMillis();
        private long lastLog = System.currentTimeMillis();
    }
}
