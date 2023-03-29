package com.giraone.kafka.pipeline.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest
class ProduceServiceIT {

    @Test
    void source() {
        StepVerifier.create(ProduceService.source(Duration.ofSeconds(1), 2))
            .expectNextCount(2)
            .verifyComplete();
    }
}