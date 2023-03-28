# Reactive Spring Boot Kafka

Example Spring WebFlux project using reactive Kafka.

## Setup

### Kafka

- Broker: `kakfa-1:9092` via [docker-compose.yml](docker/docker-compose.yml)
- Topics: `topic1,topic2`

## Testing

- For integration testing Kafka Testcontainers is used.

## CLI testing

```bash
cd docker
typeset -i i=0
while (( i < 1000 )); do ./producer.sh $i:xxx; (( i+=1 )); done
```

## Design decision

### Commit handling

Reactive Kafka supports multiple ways of acknowledging and committing offsets:

1. acknowledging only and **periodic automatic commit** (based on commit interval and/or commit batch size)
2. manual commits and disabling of automatic commit (`.commitInterval(Duration.ZERO)`and `.commitBatchSize(0)`)

## TODO

- https://github.com/reactor/reactor-kafka/issues/227 and https://projectreactor.io/docs/kafka/release/reference/#kafka-source

## Config

- [KafkaConsumerConfig](src/main/java/com/giraone/kafka/pipeline/config/KafkaConsumerConfig.java)
- [application.yml](src/main/resources/application.yml)
- [pom.xml](pom.xml)
