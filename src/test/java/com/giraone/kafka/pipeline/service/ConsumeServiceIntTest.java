package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
@TestPropertySource(locations = "classpath:test-consume.properties") // must be properties - not yaml
class ConsumeServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;


    @Override
    protected String getClientId() {
        return "ConsumeServiceIntTest";
    }

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("ConsumeServiceIntTest.setUp");

        createNewTopic(applicationProperties.getTopicB());

        this.waitForTopic(applicationProperties.getTopicB(), true);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    void passOneEvent() throws Exception {

        String topic = applicationProperties.getTopicB();
        String messageKey = Long.toString(System.currentTimeMillis());
        String messageBody = "ZWEI";
        try (ReactiveKafkaProducerTemplate<String, String> template = new ReactiveKafkaProducerTemplate<>(senderOptions)) {
            template.send(topic, messageKey, messageBody)
                .doOnSuccess(senderResult -> LOGGER.info("Sent event {} to topic {} with offset : {}",
                    messageBody, topic, senderResult.recordMetadata().offset()))
                .block();

            // We have to wait some time. We use at least the producer request timeout.
            Thread.sleep(requestTimeoutMillis);


        }
    }
}
