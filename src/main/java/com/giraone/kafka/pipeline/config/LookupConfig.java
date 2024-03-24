package com.giraone.kafka.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LookupConfig {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LookupConfig.class);

    // See https://spring.io/guides/gs/spring-data-reactive-redis
    // Not needed, when value is a String -> there is a reactiveStringRedisTemplate
    /**
     @Bean ReactiveRedisOperations<String, String> redisOperations(ReactiveRedisConnectionFactory factory) {

     final StringRedisSerializer keySerializer = new StringRedisSerializer();
     final StringRedisSerializer valueSerializer = new StringRedisSerializer();
     final RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder =
     RedisSerializationContext.newSerializationContext(keySerializer);
     final RedisSerializationContext<String, String> context = builder.value(valueSerializer).build();
     LOGGER.info("redisOperations setup with factory = {}, context = {}", factory, context);
     return new ReactiveRedisTemplate<>(factory, context);
     }
     */
}
