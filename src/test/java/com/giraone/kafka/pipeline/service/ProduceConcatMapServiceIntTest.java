package com.giraone.kafka.pipeline.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:produce/test-produce-concat-map.properties") // must be properties - not yaml
class ProduceConcatMapServiceIntTest extends ProduceServiceIntTest {

    @Override
    protected String getClientId() {
        return "ProduceConcatMapServiceIntTest";
    }

    @Disabled
    @Test
    void eventsAreProduced() throws InterruptedException {

        super.eventsAreProduced();
    }
}