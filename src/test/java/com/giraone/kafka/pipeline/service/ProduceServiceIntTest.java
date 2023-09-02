package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
@TestPropertySource(locations = "classpath:test-produce-standard.properties") // must be properties - not yaml
class ProduceServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProduceServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;

    @Autowired
    private ProduceStandardService produceStandardService;

    private Consumer<String, String> consumer;

    @Override
    protected String getClientId() {
        return "ProduceServiceIntTest";
    }

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("ProduceServiceIntTest.setUp");

        createNewTopic(applicationProperties.getTopicA());

        this.waitForTopic(applicationProperties.getTopicA(), true);
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

    @Test
    void passOneEvent() throws InterruptedException {

//        StepVerifier.create(produceStandardService.source(Duration.ofSeconds(1), 1))
//            .expectNextCount(1)
//            .verifyComplete();

        // We have to wait some time. The producer has to start! We use at least the producer request timeout.
        Thread.sleep(requestTimeoutMillis * 2);

        waitForMessages(consumer, null); // the created number does not matter

        receivedRecords.forEach(l -> l.forEach(record -> {
            LOGGER.info(record.key() + " -> " + record.value());
            assertThat(record.key()).isNotNull();
            assertThat(record.value()).contains("EINS");
        }));
    }
}