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

    // per metric, per partition
    private final Map<String, Map<String, BeforeAndNowCounter>> counterPerMetric = new HashMap<>();

    public void logRate(String metric, int partition, long offset) {
        logRate(metric, Integer.toString(partition), offset);
    }

    public void logRate(String metric) {
        logRate(metric, "-", -1);
    }

    public void logRate(String metric, String partition, long offset) {

        Map<String, BeforeAndNowCounter> counterPerPartition = counterPerMetric.get(metric);
        if (counterPerPartition == null) {
            counterPerPartition = new HashMap<>();
            counterPerMetric.put(metric, counterPerPartition);
        }
        BeforeAndNowCounter counter = counterPerPartition.get(partition);
        if (counter == null) {
            counter = new BeforeAndNowCounter();
            counterPerPartition.put(partition, counter);
        }
        counter.value++;
        final long now = System.currentTimeMillis();
        final long diffMs = now - counter.lastLog;
        if (diffMs > 1000L) {
            final long rate = (counter.value - counter.before) * 1000 / diffMs;
            LOGGER.info("{}/{}: ops={} offset={} total={}", metric, partition, rate, offset, counter.value);
            counter.lastLog = now;
            counter.before = counter.value;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class BeforeAndNowCounter {
        private long before = 0L;
        private long value = 0L;
        private long lastLog = System.currentTimeMillis();
    }
}
