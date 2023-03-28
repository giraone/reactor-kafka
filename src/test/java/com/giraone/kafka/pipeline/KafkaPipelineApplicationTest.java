package com.giraone.kafka.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class KafkaPipelineApplicationTest {

    @Test
    void contextLoadsDoesNotThrow() {
        assertDoesNotThrow(() -> KafkaPipelineApplication.main(new String[]{}));
    }
}