package com.giraone.kafka.pipeline.service.pipe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest

@TestPropertySource(locations = "classpath:pipe/test-pipe-exactly-once.properties") // must be properties - not yaml
public class PipeExactlyOnceServiceIntTest extends PipeServiceIntTest {

    @Override
    protected String getClientId() {
        return "PipeExactlyOnceServiceIntTest";
    }

    @Test
    void passMultipleEvents() throws Exception {
        super.passMultipleEvents();
    }
}
