package com.giraone.kafka.pipeline.service.pipe;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import com.giraone.kafka.pipeline.service.AbstractKafkaIntTest;
import com.giraone.kafka.pipeline.service.CounterService;
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
import reactor.util.function.Tuples;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // because init() needs ConsumerService
@TestPropertySource(locations = "classpath:pipe/test-pipe-dedup.properties") // must be properties - not yaml
class PipeDedupServiceIntTest extends AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeServiceIntTest.class);

    @Autowired
    ApplicationProperties applicationProperties;
    @Autowired
    CounterService counterService;

    private Consumer<String, String> consumer;

    @Override
    protected String getClientId() {
        return "PipeDedupServiceIntTest";
    }

    @BeforeEach
    protected void setUp() {
        LOGGER.debug("PipeDedupServiceIntTest.setUp");
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

    @Test
    void passTwoEventsWithDifferentKey() throws Exception {

        long beforeSent = counterService.getCounterSent();
        long beforeDuplicates = counterService.getCounterDuplicates();
        this.sendMessagesAndAssertReceived(
            applicationProperties.getTopicA(), consumer,
            List.of(Tuples.of("key1", "Eins"), Tuples.of("key2", "Zwei")),
            List.of(Tuples.of("key1", "Eins"), Tuples.of("key2", "Zwei"))
        );
        long afterSent = counterService.getCounterSent();
        long afterDuplicates = counterService.getCounterDuplicates();
        assertThat(afterSent - beforeSent).isEqualTo(2);
        assertThat(afterDuplicates - beforeDuplicates).isEqualTo(0);
    }

    @Test
    void passTwoEventsWithEqualKey() throws Exception {

        long beforeSent = counterService.getCounterSent();
        long beforeDuplicates = counterService.getCounterDuplicates();
        this.sendMessagesAndAssertReceived(
            applicationProperties.getTopicA(), consumer,
            List.of(Tuples.of("key3", "Drei"), Tuples.of("key3", "Three")),
            List.of(Tuples.of("key3", "Drei"))
        );
        long afterSent = counterService.getCounterSent();
        long afterDuplicates = counterService.getCounterDuplicates();
        assertThat(afterSent - beforeSent).isEqualTo(1);
        assertThat(afterDuplicates - beforeDuplicates).isEqualTo(1);
    }
}
