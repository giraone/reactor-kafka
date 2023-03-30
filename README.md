# Reactive Spring Boot Kafka

Example Spring WebFlux project using reactive Kafka.

There are 3 modes:

- Producer (periodic source, Kafka sink)
- Pipeline (Kafka source, Kafka sink)
- Consumer (Kafka source, logger sink)

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

### Pipeline

There are 3 solutions:

1. receive().flatMap(r -> send(transform(r))
2. send(receive().map(r -> transform(r)))
3. receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())

Solution 1 does not start, when there are older, but no new events.

### Threading

See [Multi threading on Kafka Send in Spring reactor Kafka](https://stackoverflow.com/questions/69891782/multi-threading-on-kafka-send-in-spring-reactor-kafka)

### Other TODOs

- receive vs. receiveAutoAck

## Performance

### Producer

- 9 ops for 100ms produce delay between each event
- 64 ops for 1ms produce delay between each event
- 700 ops for 0ms produce delay between each event

```
2023-03-30T23:55:31.103+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/3: ops=161 total=831 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:31.112+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/1: ops=143 total=808 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:31.706+02:00  INFO 9404 --- [ender-896138248] c.g.k.pipeline.service.CounterService    : PRD/-: ops=642 total=3713 thread=reactor-kafka-sender-896138248
2023-03-30T23:55:32.101+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/2: ops=145 total=967 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:32.104+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/0: ops=164 total=967 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:32.106+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/3: ops=137 total=969 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:32.126+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/1: ops=176 total=987 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:32.893+02:00  INFO 9404 --- [ender-896138248] c.g.k.pipeline.service.CounterService    : PRD/-: ops=647 total=4481 thread=reactor-kafka-sender-896138248
2023-03-30T23:55:33.104+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/2: ops=171 total=1139 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:33.114+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/3: ops=148 total=1119 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:33.123+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/0: ops=176 total=1147 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:33.132+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/1: ops=172 total=1161 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:34.041+02:00  INFO 9404 --- [ender-896138248] c.g.k.pipeline.service.CounterService    : PRD/-: ops=668 total=5249 thread=reactor-kafka-sender-896138248
2023-03-30T23:55:34.105+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/2: ops=161 total=1301 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:34.123+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/3: ops=169 total=1290 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:55:34.130+02:00  INFO 9404 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SND/0: ops=171 total=1320 thread=kafka-producer-network-thread | producer-1
```

### Pipeline

- 600 ops for 0ms transform delay in each pipeline processing
- 10 ops, when direct commit per event is used

```
2023-03-30T23:56:07.579+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/3: ops=497 total=641 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:08.321+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/3: ops=529 total=972 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:08.639+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/3: ops=543 total=1217 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:09.964+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=441 total=585 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:10.062+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=564 total=566 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:10.970+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=572 total=1161 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:11.063+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=587 total=1154 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:12.064+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=635 total=1790 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:12.174+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=637 total=1929 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:13.065+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=662 total=2453 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:13.337+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=660 total=2697 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:14.067+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=665 total=3120 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:14.495+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=663 total=3465 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:15.070+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=664 total=3786 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:15.724+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/2: ops=624 total=4233 thread=reactor-kafka-sender-777341499
2023-03-30T23:56:16.913+02:00  INFO 15976 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : ACK/1: ops=613 total=614 thread=kafka-producer-network-thread | producer-1
2023-03-30T23:56:16.954+02:00  INFO 15976 --- [ender-777341499] c.g.k.pipeline.service.CounterService    : RCV/1: ops=576 total=709 thread=reactor-kafka-sender-777341499
```

with groupBy

- ~ 100 per thread

```
2023-03-31T00:43:26.555+02:00  INFO 2172 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RCV/0: ops=204 total=19486 thread=worker-3
2023-03-31T00:43:27.594+02:00  INFO 2172 --- [       worker-5] c.g.k.pipeline.service.CounterService    : RCV/2: ops=53 total=56 thread=worker-5
2023-03-31T00:43:27.594+02:00  INFO 2172 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RCV/0: ops=65 total=19554 thread=worker-3
2023-03-31T00:43:27.669+02:00  INFO 2172 --- [       worker-7] c.g.k.pipeline.service.CounterService    : RCV/3: ops=71 total=75 thread=worker-7
2023-03-31T00:43:27.669+02:00  INFO 2172 --- [       worker-9] c.g.k.pipeline.service.CounterService    : RCV/1: ops=54 total=57 thread=worker-9
2023-03-31T00:43:28.607+02:00  INFO 2172 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RCV/0: ops=96 total=19652 thread=worker-3
2023-03-31T00:43:28.607+02:00  INFO 2172 --- [       worker-5] c.g.k.pipeline.service.CounterService    : RCV/2: ops=97 total=155 thread=worker-5
2023-03-31T00:43:28.691+02:00  INFO 2172 --- [       worker-9] c.g.k.pipeline.service.CounterService    : RCV/1: ops=126 total=186 thread=worker-9
2023-03-31T00:43:28.691+02:00  INFO 2172 --- [       worker-7] c.g.k.pipeline.service.CounterService    : RCV/3: ops=97 total=175 thread=worker-7
2023-03-31T00:43:29.628+02:00  INFO 2172 --- [       worker-5] c.g.k.pipeline.service.CounterService    : RCV/2: ops=107 total=265 thread=worker-5
2023-03-31T00:43:29.628+02:00  INFO 2172 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RCV/0: ops=120 total=19775 thread=worker-3
2023-03-31T00:43:29.716+02:00  INFO 2172 --- [       worker-9] c.g.k.pipeline.service.CounterService    : RCV/1: ops=119 total=308 thread=worker-9
2023-03-31T00:43:29.716+02:00  INFO 2172 --- [       worker-7] c.g.k.pipeline.service.CounterService    : RCV/3: ops=97 total=275 thread=worker-7
2023-03-31T00:43:30.651+02:00  INFO 2172 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RCV/0: ops=111 total=19889 thread=worker-3
2023-03-31T00:43:30.652+02:00  INFO 2172 --- [       worker-5] c.g.k.pipeline.service.CounterService    : RCV/2: ops=107 total=375 thread=worker-5
2023-03-31T00:43:30.795+02:00  INFO 2172 --- [       worker-9] c.g.k.pipeline.service.CounterService    : RCV/1: ops=109 total=426 thread=worker-9
2023-03-31T00:43:30.795+02:00  INFO 2172 --- [       worker-7] c.g.k.pipeline.service.CounterService    : RCV/3: ops=96 total=379 thread=worker-7
2023-03-31T00:43:31.715+02:00  INFO 2172 --- [       worker-5] c.g.k.pipeline.service.CounterService    : RCV/2: ops=110 total=493 thread=worker-5
```

### Consume

- ~ 70000 per second

```
2023-03-31T00:58:17.751+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : RCV/3: ops=67325 total=74395 thread=reactive-kafka-consume-1
2023-03-31T00:58:17.751+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : ACK/3: ops=67264 total=74395 thread=reactive-kafka-consume-1
2023-03-31T00:58:17.775+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : RCV/2: ops=71073 total=74556 thread=reactive-kafka-consume-1
2023-03-31T00:58:17.775+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : ACK/2: ops=71005 total=74556 thread=reactive-kafka-consume-1
2023-03-31T00:58:17.817+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : RCV/1: ops=72667 total=74557 thread=reactive-kafka-consume-1
2023-03-31T00:58:17.817+02:00  INFO 16988 --- [kafka-consume-1] c.g.k.pipeline.service.CounterService    : ACK/1: ops=72667 total=74557 thread=reactive-kafka-consume-1
```

## Config

- [KafkaProducerConfig](src/main/java/com/giraone/kafka/pipeline/config/KafkaProducerConfig.java)
- [KafkaConsumerConfig](src/main/java/com/giraone/kafka/pipeline/config/KafkaConsumerConfig.java)
- [application.yml](src/main/resources/application.yml)
- [pom.xml](pom.xml)

## Unused Code

### Producer

```java
class ProduceService {
    public void run(String... args) { // source.flatMap(t -> send(SenderRecord(t)))
        source(applicationProperties.getProduceInterval(), Integer.MAX_VALUE)
            .flatMap(tuple -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, tuple.getT1(), tuple.getT2());
                return reactiveKafkaProducerTemplate.send(SenderRecord.create(producerRecord, tuple.getT1()));
            })
            .doOnNext(senderResult -> {
                LOGGER.debug("  > t={}/p={}/o={}", senderResult.recordMetadata().topic(),
                    senderResult.recordMetadata().partition(), senderResult.recordMetadata().offset());
                counterService.logRate("SND", senderResult.recordMetadata().partition());
            })
            .doOnError(e -> LOGGER.error("Send failed", e))
            .subscribe();
    }
}
```

### Pipeline

```java
class PipelineService {
    public void run(String... args) { // receive().flatMap(r -> send(transform(r))
        
        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> counterService.logRate("RCV", receiverRecord.partition()))
            .doOnNext(receiverRecord -> {
                LOGGER.debug(">>> k={}/v={}", receiverRecord.key(), receiverRecord.value());
                LOGGER.debug("  > t={}/p={}/o={}", receiverRecord.topic(), receiverRecord.partition(), receiverRecord.receiverOffset());
            })
            .flatMap(receiverRecord -> {
                final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicOutput, receiverRecord.key(), receiverRecord.value());
                return reactiveKafkaProducerTemplate.send(SenderRecord.create(producerRecord, receiverRecord.receiverOffset()));
            })
            .doOnNext(this::ack)
            .subscribe();
    }
}
```