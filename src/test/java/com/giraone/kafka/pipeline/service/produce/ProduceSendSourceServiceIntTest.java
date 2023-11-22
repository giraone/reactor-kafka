package com.giraone.kafka.pipeline.service.produce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:produce/test-produce-send-source.properties") // must be properties - not yaml
class ProduceSendSourceServiceIntTest extends ProduceServiceIntTest {

    @Override
    protected String getClientId() {
        return "ProduceSendSourceServiceIntTest";
    }

    @Test
    void eventsAreProduced() throws InterruptedException {
        super.eventsAreProduced();
    }
}