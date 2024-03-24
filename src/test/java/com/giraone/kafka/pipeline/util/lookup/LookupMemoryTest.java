package com.giraone.kafka.pipeline.util.lookup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LookupMemoryTest {

    private LookupMemory lookupService = new LookupMemory();

    @Test
    void lookup() {

        lookupService.put("k1", "v1").block();
        lookupService.put("k2", "v2").block();
        assertThat(lookupService.lookup("k1").block()).isEqualTo("v1");
        assertThat(lookupService.lookup("k2").block()).isEqualTo("v2");
        assertThat(lookupService.lookup("k3").block()).isNull();
    }
}