package com.giraone.kafka.pipeline.util.lookup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest
class LookupRedisIntTest {

    // See https://www.baeldung.com/spring-boot-redis-testcontainers
    static {
        GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2.4-alpine")).withExposedPorts(6379);
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @Autowired
    private LookupService lookupService;

    @Test
    void timeout() throws InterruptedException {

        StepVerifier.create(lookupService.put("k1", "v1"))
            .expectNext(true)
            .verifyComplete();

        StepVerifier.create(lookupService.lookup("k1"))
            .expectNext("v1")
            .verifyComplete();

        Thread.sleep(2000L);

        StepVerifier.create(lookupService.lookup("k1"))
            .expectNextCount(0L)
            .verifyComplete();
    }
}