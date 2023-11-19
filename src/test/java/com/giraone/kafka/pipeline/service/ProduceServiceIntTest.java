package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
abstract class ProduceServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProduceServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;
    @Autowired
    CounterService counterService;

    private Consumer<String, String> consumer;

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("ProduceServiceIntTest.setUp");
        createNewTopic(applicationProperties.getTopicA());
        consumer = createConsumer(applicationProperties.getTopicA());
        LOGGER.info("Consumer for \"{}\" created. Assignments = {}", applicationProperties.getTopicA(), consumer.assignment());
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
            LOGGER.info("Consumer for \"{}\" closed.", applicationProperties.getTopicA());
        }
    }

    void eventsAreProduced() throws InterruptedException {

        // When the test ist started, the events are already sent by the producer
        assertThat(counterService.getCounterProduced()).isGreaterThan(0L);
        assertThat(counterService.getCounterSent()).isGreaterThan(0L);
        waitForMessages(consumer, null); // the created number does not matter
        assertThat(receivedRecords.size()).isGreaterThan(0);
        receivedRecords.forEach(l -> l.forEach(record -> {
            LOGGER.info(record.key() + " -> " + record.value());
            assertThat(record.key()).isNotNull();
            assertThat(record.value()).isNotNull();
        }));
    }
}