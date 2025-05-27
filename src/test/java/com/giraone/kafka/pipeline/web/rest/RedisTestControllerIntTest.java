package com.giraone.kafka.pipeline.web.rest;

import com.giraone.kafka.pipeline.util.lookup.LookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = { "application.mode=PipeDedup" })
@AutoConfigureWebTestClient(timeout = "30000") // 30 seconds
class RedisTestControllerIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTestControllerIntTest.class);

    // See https://www.baeldung.com/spring-boot-redis-testcontainers
    static {
        GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.3-alpine")).withExposedPorts(6379);
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
        LOGGER.info("Redis setup done.");
    }

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private LookupService lookupService;

    @BeforeEach
    void setup() {
        lookupService.put("k1", "v1").block();
        lookupService.put("k2", "v2").block();
        LOGGER.info("Test values added to lookupService={}", lookupService.getClass().getSimpleName());
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
        EntityExchangeResult<byte[]> result = webTestClient.get().uri("/api/lookup/{key}", "k4")
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
        EntityExchangeResult<byte[]> result = webTestClient.put().uri("/api/lookup/{key}", "k1").bodyValue("v1-new")
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
        assertThat(new String(body, StandardCharsets.UTF_8))
            .contains("k1=v1;")
            .contains("k2=v2;");
    }
}