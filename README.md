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

- 9 ops for 100ms produce delay between each event
- 64 ops for 1ms produce delay between each event
- 700 ops for 0ms produce delay between each event

```
2023-03-31T14:18:57.841+02:00  INFO 1356 --- [nder-1601935322] c.g.k.pipeline.service.CounterService    : PROD/-: ops=221 offset=-1 total=257 thread=reactor-kafka-sender-1601935322
2023-03-31T14:18:58.110+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops=55 offset=121613 total=56 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:58.119+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops=81 offset=121839 total=82 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:58.129+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops=67 offset=121849 total=69 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:58.145+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops=70 offset=119330 total=71 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:59.018+02:00  INFO 1356 --- [nder-1601935322] c.g.k.pipeline.service.CounterService    : PROD/-: ops=326 offset=-1 total=641 thread=reactor-kafka-sender-1601935322
2023-03-31T14:18:59.125+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops=77 offset=121917 total=160 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:59.136+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops=88 offset=121938 total=158 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:59.146+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops=78 offset=121694 total=137 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:18:59.155+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops=93 offset=119424 total=165 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:00.123+02:00  INFO 1356 --- [nder-1601935322] c.g.k.pipeline.service.CounterService    : PROD/-: ops=347 offset=-1 total=1025 thread=reactor-kafka-sender-1601935322
2023-03-31T14:19:00.127+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops=97 offset=122015 total=258 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:00.137+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops=81 offset=122020 total=240 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:00.147+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops=80 offset=121775 total=218 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:00.157+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops=88 offset=119513 total=254 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:01.137+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops=99 offset=122115 total=358 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:01.151+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops=125 offset=122147 total=367 thread=kafka-producer-network-thread | producer-1
2023-03-31T14:19:01.157+02:00  INFO 1356 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops=97 offset=121873 total=316 thread=kafka-producer-network-thread | producer-1
```

### Pipe

- 600 ops for 0ms transform delay in each pipe processing
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

- [KafkaProducerConfig](src/main/java/com/giraone/kafka/pipe/config/KafkaProducerConfig.java)
- [KafkaConsumerConfig](src/main/java/com/giraone/kafka/pipe/config/KafkaConsumerConfig.java)
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
                counterService.logRate("SEND", senderResult.recordMetadata().partition());
            })
            .doOnError(e -> LOGGER.error("Send failed", e))
            .subscribe();
    }
}
```

### Pipe

```java
class PipeService {
    public void run(String... args) { // receive().flatMap(r -> send(transform(r))
        
        reactiveKafkaConsumerTemplate.receive()
            .doOnNext(receiverRecord -> counterService.logRate("RECV", receiverRecord.partition()))
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