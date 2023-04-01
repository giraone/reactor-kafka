package com.giraone.kafka.pipeline.service;

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

    public void logRate(String metric, int partition, long offset) {
        logRateInternal(metric, partition, offset);
    }

    public void logRate(String metric) {
        logRateInternal(metric, -1, -1);
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
