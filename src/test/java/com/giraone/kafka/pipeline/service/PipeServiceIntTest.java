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
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
class PipeServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;

    private Consumer<String, String> consumer;

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("PipeServiceIntTest.setUp");

        createNewTopic(applicationProperties.getTopic1());
        createNewTopic(applicationProperties.getTopic2());

        this.waitForTopic(applicationProperties.getTopic1(), true);

        consumer = createConsumer(applicationProperties.getTopic1());
        LOGGER.info("Consumer for \"{}\" created.", applicationProperties.getTopic1());
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
            LOGGER.info("Consumer for \"{}\" closed.", applicationProperties.getTopic1());
        }
    }

    @Test
    void passOneEvent() throws Exception {

        String topic = applicationProperties.getTopic1();
        String messageKey = Long.toString(System.currentTimeMillis());
        String messageBody = "Eins";
        try (ReactiveKafkaProducerTemplate<String, String> template = new ReactiveKafkaProducerTemplate<>(senderOptions)) {
            template.send(topic, messageKey, messageBody)
                .doOnSuccess(senderResult -> LOGGER.info("Sent event {} to topic {} with offset : {}",
                    messageBody, topic, senderResult.recordMetadata().offset()))
                .block();

            // We have to wait some time. We use at least the producer request timeout.
            Thread.sleep(requestTimeoutMillis);

            waitForMessages(consumer, 1);

            receivedRecords.forEach(l -> {
                l.forEach(record -> {
                    LOGGER.info(record.key() + " -> " + record.value());
                    assertThat(record.key()).isNotNull();
                    assertThat(record.value()).contains("EINS");
                });
            });
        }
    }
}
