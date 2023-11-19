package com.giraone.kafka.pipeline.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest

@TestPropertySource(locations = "classpath:pipe/test-pipe-send-receive.properties") // must be properties - not yaml
public class PipeSendReceiveServiceIntTest extends PipeServiceIntTest {

    @Override
    protected String getClientId() {
        return "PipeSendReceiveServiceIntTest";
    }

    @Test
    void passMultipleEvents() throws Exception {
        super.passMultipleEvents();
    }
}
