package com.giraone.kafka.pipeline.util.lookup;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class LookupRedis implements LookupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LookupRedis.class);

    private final ReactiveRedisOperations<String, String> keyValueOps;
    private final Duration ttl;

    public LookupRedis(ReactiveRedisOperations<String, String> keyValueOps,
                       ApplicationProperties applicationProperties) {

        this.keyValueOps = keyValueOps;
        this.ttl = applicationProperties.getLookup().getTtl();
        LOGGER.info("TTL for Redis is {}", ttl);
    }

    @Override
    public Mono<Boolean> put(String key, String value) {
        return keyValueOps.opsForValue().set(key, value, ttl);
    }

    @Override
    public Mono<String> lookup(String key) {
        return keyValueOps.opsForValue().get(key);
    }

    @Override
    public Flux<Map.Entry<String, String>> fetchAll() {
        return fetchAllKeys()
            .flatMap(key -> lookup(key)
                .map(value -> Map.entry(key, value)));
    }

    public Flux<String> fetchAllKeys() {
        return keyValueOps.keys("*");
    }
}
