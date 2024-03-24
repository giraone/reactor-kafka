package com.giraone.kafka.pipeline.web.rest;

import com.giraone.kafka.pipeline.util.lookup.LookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(prefix = "application", name = "mode", havingValue = "PipeDedup")
public class RedisTestController {

    protected static final Logger LOGGER = LoggerFactory.getLogger(RedisTestController.class);

    private final LookupService lookupService;

    public RedisTestController(LookupService lookupService) {
        this.lookupService = lookupService;
        LOGGER.info("RedisTestController using {}", lookupService.getClass().getSimpleName());
    }

    @PutMapping(value = "/lookup/{key}", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> put(@PathVariable String key, @RequestBody String value) {
        return lookupService.put(key, value)
            .map(Object::toString);
    }

    @GetMapping(value = "/lookup/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> lookup(@PathVariable String key) {
        return lookupService.lookup(key)
            .switchIfEmpty(Mono.just(""));
    }

    @GetMapping(value = "/lookup", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> lookup() {
        return lookupService.fetchAll()
            .map(kv -> kv.getKey() + "=" + kv.getValue() + ";");
    }
}
