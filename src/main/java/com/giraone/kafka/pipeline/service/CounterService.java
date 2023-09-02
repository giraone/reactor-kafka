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

    private final Counter counterSend;
    private final Counter counterReceive;
    private final Counter counterAcknowledge;
    private final Counter counterCommit;
    private final Counter counterProduced;
    private final Counter counterProcessed;
    private final Counter counterError;
    private final Counter counterMainLoopStarted;
    private final Counter counterMainLoopStopped;

    public CounterService(MeterRegistry registry) {
        this.counterSend = registry.counter("pipeline.send");
        this.counterReceive = registry.counter("pipeline.receive");
        this.counterAcknowledge = registry.counter("pipeline.acknowledge");
        this.counterCommit = registry.counter("pipeline.commit");
        this.counterProduced = registry.counter("pipeline.produced");
        this.counterProcessed = registry.counter("pipeline.processed");
        this.counterError = registry.counter("pipeline.error");
        this.counterMainLoopStarted = registry.counter("pipeline.mainloop.started");
        this.counterMainLoopStopped = registry.counter("pipeline.mainloop.stopped");
    }

    public void logRateSend(int partition, long offset) {
        logRateInternal("SEND", partition, offset);
        counterSend.increment();
    }

    public void logRateReceive(int partition, long offset) {
        logRateInternal("RECV", partition, offset);
        counterReceive.increment();
    }

    public void logRateAcknowledge(int partition, long offset) {
        logRateInternal("ACKN", partition, offset);
        counterAcknowledge.increment();
    }

    public void logRateCommit(int partition, long offset) {
        logRateInternal("CMMT", partition, offset);
        counterCommit.increment();
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
                LOGGER.info("{}/{}: ops={} offset={} total={}",
                    metric, partition, totalRate, offset, counterTotal.value);
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
