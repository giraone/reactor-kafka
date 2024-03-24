package com.giraone.kafka.pipeline.util.lookup;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * In-Memory implementation for lookup
 */
public class LookupInMemory implements LookupService {

    private final Map<String, String> map = new HashMap<>();

    @Override
    public Mono<Boolean> put(String key, String value) {
        map.put(key, value);
        return Mono.just(true);
    }

    @Override
    public Mono<String> lookup(String key) {
        final String value = map.get(key);
        return value != null ? Mono.just(value) : Mono.empty();
    }

    @Override
    public Flux<Map.Entry<String, String>> fetchAll() {
        return Flux.fromIterable(map.entrySet());
    }
}
