package com.giraone.kafka.pipeline.service.produce;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:produce/test-produce-transactional.properties") // must be properties - not yaml
class ProduceTransactionalServiceIntTest extends ProduceServiceIntTest {

    @Override
    protected String getClientId() {
        return "ProduceTransactionalServiceIntTest";
    }

    @Disabled
    @Test
    void eventsAreProduced() throws InterruptedException {
        super.eventsAreProduced();
    }
}