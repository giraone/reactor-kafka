package com.giraone.kafka.pipeline.service.consume;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.AbstractKafkaIntTest;
import com.giraone.kafka.pipeline.service.CounterService;
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
import reactor.util.function.Tuples;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
@TestPropertySource(locations = "classpath:consume/test-consume.properties") // must be properties - not yaml
class ConsumeServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumeServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;
    @Autowired
    CounterService counterService;

    @Override
    protected String getClientId() {
        return "ConsumeServiceIntTest";
    }

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("ConsumeServiceIntTest.setUp");
        createNewTopic(applicationProperties.getTopicB());
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    void receiveOneEvent() throws Exception {

        long before = counterService.getCounterReceived();
        send(applicationProperties.getTopicB(), Tuples.of("9", "nine"));
        // We have to wait some time. We use at least the producer request timeout.
        Thread.sleep(requestTimeoutMillis * 2);
        long after = counterService.getCounterReceived();
        assertThat(after - before).isEqualTo(1);
    }
}
