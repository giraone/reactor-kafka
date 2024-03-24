package com.giraone.kafka.pipeline.util.lookup;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "application.lookup", name = "in-memory", havingValue = "false", matchIfMissing = true)
public class LookupRedis implements LookupService {

    private final ReactiveRedisConnectionFactory factory;
    private final ReactiveRedisOperations<String, String> keyValueOps;
    private final Duration ttl;

    public LookupRedis(ReactiveRedisConnectionFactory factory,
                       ReactiveRedisOperations<String, String> keyValueOps,
                       ApplicationProperties applicationProperties) {
        this.factory = factory;
        this.keyValueOps = keyValueOps;
        this.ttl = applicationProperties.getLookup().getTtl();
    }

    @PostConstruct
    public void loadData() {
        factory.getReactiveConnection().serverCommands().flushAll().thenMany(
                Flux.just(
                        Tuples.of("k1", "v1"),
                        Tuples.of("k2", "v2")
                    )
                    .flatMap(tuple2 -> keyValueOps.opsForValue().set(tuple2.getT1(), tuple2.getT2())))
            .thenMany(keyValueOps.keys("*")
                .flatMap(keyValueOps.opsForValue()::get))
            .subscribe(System.out::println);
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
