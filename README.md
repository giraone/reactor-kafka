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
- Performance (all local): Total rate with 4 partitions approx. 650 events per second (produce only)
- Performance (DATEV, 1 instance): Total rate with 16 partitions (produce only):
  - ack=all: 270 ops / 16-17 ops/partition
  - ack=1, batchsize=262144, linger=<not-set>: 610 ops
  - ack=1, batchsize=262144: linger=0: 540 ops
  - ack=1, batchsize=16384: 530 ops
  - ack=0, batchsize=262144: inger=<not-set>: 12000 ops
  - ack=1, linger=100: 9 ops (!)
  - ack=1, batchsize=262144, linger=10: 82 ops
    
### ProducerFlatMap

- SEND on `kafka-producer-network-thread | producer-1`
- PRODUCE on ` generate-1`

### ProduceTransactional

t.b.d.: *needs multiple topics*

### PipePartitioned

- RECEIVE on `thread=worker-X` for each partition X
- TASK on `thread=worker-X` for each partition X

- Performance (Kafka/service local, after 100.000 events available, pipe only): Total rate with 4 partitions approx. **33.000 events per second**.
- Performance (DATEV, after 100.000 events available, pipe only):
  - 16 partitions / 16 Threads / 1 instance (commitBatchSize=10, maxPollRecords=500, maxPollInterval=PT30S): **33.000 events per second**.
  - 16 partitions / 8 Threads / 2 instances (commitBatchSize=10, maxPollRecords=500, maxPollInterval=PT30S): **38.000 events per second**.
### PipeReceiveSend

- RECEIVE on `kafka-producer-network-thread | producer-1`
- TASK on `kafka-producer-network-thread | producer-1`

Performance (Kafka/service local, after 100.000 events available, pipe only):
Total rate with 4 partitions approx. **71.000 events per second**.

### PipeSendReceive

- RECEIVE on `reactor-kafka-sender-9999`
- TASK on `reactor-kafka-sender-9999`

Performance (Kafka/service local, produce only):
Total rate with 4 partitions approx. **650 events per second**.

### PipeExactlyOnce

- RECEIVE on `kafka-producer-network-thread | producer-1`
- TASK on `kafka-producer-network-thread | producer-1`

Performance (Kafka/service local, after 100.000 events available, pipe only):
Total rate with 4 partitions approx. **x events per second**.

### Consume

- RECEIVE on `reactive-kafka-Consume-1`
- ACKNOWLEDGE on `reactive-kafka-Consume-1`

Performance (Kafka/service local, after 100.000 events available, consume only):
Total rate with 4 partitions approx. **1.000.000 events per second**.

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