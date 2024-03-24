package com.giraone.kafka.pipeline.util.lookup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "application.lookup", name = "in-memory", havingValue = "true")
public class LookupMemory implements LookupService {

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
