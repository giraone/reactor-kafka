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
import reactor.util.function.Tuples;

import java.util.List;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
abstract class PipeServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;

    private Consumer<String, String> consumer;

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("PipeServiceIntTest.setUp");
        createNewTopic(applicationProperties.getTopicA());
        createNewTopic(applicationProperties.getTopicB());
        consumer = createConsumer(applicationProperties.getTopicB());
        LOGGER.info("Consumer for \"{}\" created. Assignments = {}", applicationProperties.getTopicB(), consumer.assignment());
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
            LOGGER.info("Consumer for \"{}\" closed.", applicationProperties.getTopicB());
        }
    }

    void passMultipleEvents() throws Exception {

        this.sendMessagesAndAssertReceived(
            applicationProperties.getTopicA(), consumer,
            List.of(Tuples.of("key1", "Eins"), Tuples.of("key2", "Zwei"), Tuples.of("key3", "Drei")),
            List.of(Tuples.of("key1", "EINS"), Tuples.of("key2", "ZWEI"), Tuples.of("key3", "DREI"))
        );
    }
}
