package com.giraone.kafka.pipeline.service.pipe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest

@TestPropertySource(locations = "classpath:pipe/test-pipe-partitioned.properties") // must be properties - not yaml
public class PipePartitionedServiceIntTest extends PipeServiceIntTest {

    @Override
    protected String getClientId() {
        return "PipePartitionedServiceIntTest";
    }

    @Test
    void passMultipleEvents() throws Exception {
        super.passMultipleEvents();
    }
}
