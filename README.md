# Reactive Spring Boot Kafka

Example Spring WebFlux project using [reactive Kafka](https://projectreactor.io/docs/kafka/release/reference/).

There are the following application modes:

- Producer (periodic source, Kafka sink)
- Pipe (Kafka source, Kafka sink)
  1. Pipe with `send(receive().map(r -> transform(r)))` (PipeSendReceive)
  2. Pipe with `receive().flatMap(r -> send(transform(r))` (PipeReceiveSend)
  3. Pipe with `receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())` (PipePartitioned)
  4. Pipe with *exactly-once delivery* (PipeExactlyOnce)
- Consumer (Kafka source, logger sink)

Solution Pipe 2 and Pipe 3 do not start, when there are older events in topic, but no new events arrive!!!

## Setup

### Kafka

- Broker: `kakfa-1:9092` via [docker-compose.yml](docker/docker-compose.yml)
- Topics: `topic1,topic2`

## Testing

- For integration testing Kafka Testcontainers is used.

## Design decision

### Commit handling

Reactive Kafka supports multiple ways of acknowledging and committing offsets:

1. acknowledging only and **periodic automatic commit** (based on commit interval and/or commit batch size)
2. manual commits and disabling of automatic commit (`.commitInterval(Duration.ZERO)`and `.commitBatchSize(0)`)

### Threading

See [Multi threading on Kafka Send in Spring reactor Kafka](https://stackoverflow.com/questions/69891782/multi-threading-on-kafka-send-in-spring-reactor-kafka)

### Other TODOs

- receive vs. receiveAutoAck

## Performance

### Producer

- SEND on `kafka-producer-network-thread | producer-1`
- PRODUCE on `reactor-kafka-sender-9999999`

### ProducerFlatMap

- SEND on `kafka-producer-network-thread | producer-1`
- PRODUCE on ` generate-1`

### ProduceTransactional

t.b.d.

### PipeSendReceive

- RECEIVE on `reactor-kafka-sender-9999`
- TASK on `reactor-kafka-sender-9999`

### PipePartitioned

- RECEIVE on `thread=worker-X` for each partition X
- TASK on `thread=worker-X` for each partition X

### PipeReceiveSend

- RECEIVE on `kafka-producer-network-thread | producer-1`
- TASK on `kafka-producer-network-thread | producer-1`

### Consume

- RECEIVE on `reactive-kafka-Consume-1`
- ACKNOWLEDGE on `reactive-kafka-Consume-1`

## Config

- [KafkaProducerConfig](src/main/java/com/giraone/kafka/pipe/config/KafkaProducerConfig.java)
- [KafkaConsumerConfig](src/main/java/com/giraone/kafka/pipe/config/KafkaConsumerConfig.java)
- [application.yml](src/main/resources/application.yml)
- [pom.xml](pom.xml)

### Extra code

```java
class Service {

        LOGGER.debug(">>> k={}/v={}", receiverRecord.key(), receiverRecord.value());
        LOGGER.debug("  > t={}/p={}/o={}", receiverRecord.topic(), receiverRecord.partition(), receiverRecord.receiverOffset());
        LOGGER.debug("  < k={}/t={}/p={}/o={}", senderResult.correlationMetadata().offset(), senderResult.recordMetadata().topic(),
            senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
}
```