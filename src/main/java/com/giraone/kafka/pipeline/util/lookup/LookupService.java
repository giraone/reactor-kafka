package com.giraone.kafka.pipeline.util.lookup;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * A reactive service proivding a "lookup table" (basically a <code>Map<String,String></code>)
 */
@Service
public interface LookupService {

    /**
     * Put a value into the lookup table for a given key
     *
     * @param key the key of the key pair
     * @param value the value of the key pair
     * @return true/false
     */
    Mono<Boolean> put(String key, String value);

    /**
     * Lookup a value by its key
     *
     * @param key the key for which the value lookep up
     * @return a value or Mono.void()
     */
    Mono<String> lookup(String key);

    /**
     * Return al key pairs of the lookup table
     *
     * @return Fux of key pairs
     */
    Flux<Map.Entry<String, String>> fetchAll();
}
