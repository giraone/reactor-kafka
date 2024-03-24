package com.giraone.kafka.pipeline.web.rest;

import com.giraone.kafka.pipeline.util.lookup.LookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient(timeout = "30000") // 30 seconds
class RedisTestControllerIntTest {

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private LookupService lookupService;

    @BeforeEach
    void setup() {
        lookupService.put("k1", "v1").block();
        lookupService.put("k2", "v2").block();
    }

    @Test
    void lookup_found() {
        // act/assert
        EntityExchangeResult<byte[]> result = webTestClient.get().uri("/api/lookup/{key}", "k1")
            .accept(MediaType.TEXT_PLAIN)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .returnResult();
        // assert
        byte[] body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("v1");
    }

    @Test
    void lookup_not_found() {
        // act/assert
        EntityExchangeResult<byte[]> result = webTestClient.get().uri("/api/lookup/{key}", "k3")
            .accept(MediaType.TEXT_PLAIN)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .returnResult();
        // assert
        byte[] body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    void put() {
        // act/assert
        EntityExchangeResult<byte[]> result = webTestClient.put().uri("/api/lookup/{key}", "k3").bodyValue("v3")
            .accept(MediaType.TEXT_PLAIN)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .returnResult();
        // assert
        byte[] body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("true");
    }

    @Test
    void testLookup() {
        // act/assert
        EntityExchangeResult<byte[]> result = webTestClient.get().uri("/api/lookup")
            .accept(MediaType.TEXT_PLAIN)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .returnResult();
        // assert
        byte[] body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("k1=v1;k2=v2;");
    }
}