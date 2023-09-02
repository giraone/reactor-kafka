package com.giraone.kafka.pipeline.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test-produce.properties") // must be properties - not yaml
class ProduceServiceIT {

    @Autowired
    private ProduceService produceService;

    @Test
    void source() {
        StepVerifier.create(produceService.source(Duration.ofSeconds(1), 2))
            .expectNextCount(2)
            .verifyComplete();
    }
}