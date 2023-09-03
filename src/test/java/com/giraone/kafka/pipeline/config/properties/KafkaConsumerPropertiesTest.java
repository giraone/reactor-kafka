package com.giraone.kafka.pipeline.config.properties;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.scheduler.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerPropertiesTest {

    @ParameterizedTest
    @CsvSource(value = {
        "parallel||Schedulers.parallel()",
        "newParallel||parallel(4,\"newParallelConsumer\")",
        "newParallel|2|parallel(2,\"newParallelConsumer\")",
        "newBoundedElastic||boundedElastic(\"newElasticConsumer\",maxThreads=4,maxTaskQueuedPerThread=256,ttl=60s",
        "newBoundedElastic|2|boundedElastic(\"newElasticConsumer\",maxThreads=2,maxTaskQueuedPerThread=64,ttl=60s",
    }, delimiterString = "|")
    void buildScheduler(String schedulerType, Integer concurrency, String expectedToString) {

        KafkaConsumerProperties agentConsumerProperties = new KafkaConsumerProperties();
        agentConsumerProperties.setSchedulerType(schedulerType);
        if (concurrency != null) {
            agentConsumerProperties.setConcurrency(concurrency);
        }
        Scheduler scheduler = agentConsumerProperties.buildScheduler();
        assertThat(scheduler.toString()).startsWith(expectedToString);
    }
}