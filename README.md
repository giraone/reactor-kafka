# Reactive Spring Boot Kafka

A Spring WebFlux project using [reactive Kafka](https://projectreactor.io/docs/kafka/release/reference/)
for performance and load testing different solutions, hwo to use *reactor kafka*.

Basically the topology is

- there is a producer (mode = `produce`)
- there is a processor (mode = `pipe`)
- there is a consumer (mode = `consume`)

The application code contains all modes (environment variable `APPLICATION_MODE`) in one application:

- Producer (periodic source, Kafka sink)
- Pipe (Kafka source, Kafka sink)
  1. Pipe with `send(receive().map(r -> transform(r)))` (PipeSendReceive)
  2. Pipe with `receive().flatMap(r -> send(transform(r))` (PipeReceiveSend)
  3. Pipe with `receive().groupBy(partition).flatMap(r -> send(transform(r)).sample().concatMap(s -> s.commit())` (PipePartitioned)
  4. Pipe with *exactly-once delivery* (PipeExactlyOnce)
- Consumer (Kafka source, logger sink)

Solution Pipe 2 and Pipe 3 do not start, when there are older events in topic, but no new events arrive!!!

## Setup

### Build and Run

```shell
mvn package
dockerize.sh
cd docker

# start all processes
docker-compose up -d

# start only subsystems, e.g. when running Spring Boot services locally and/or to manipulate topics before "topic auto creation" runs 
docker-compose -f docker-compose-subsystems-only.yml up -d
./kafka-create-topics.sh
cd ..
./consume.sh
./pipe.sh
./produce.sh
```

### LOKI

LOKI logging must be activated via Spring profile *loki* (`-Dspring.profiles.active=loki`).

### Kafka

- Broker: `kakfa-1:9092` via [docker-compose.yml](docker/docker-compose.yml)
- Topics: `a1,b1`
- Group-Ids:
  - `pipe-default` and `consume-default` for running locally
  - `pipe-docker` and `consume-docker` for running within docker

### Docker containers

There are 8 containers

- produce
- pipe
- consume
- Kafka Zookeeper
- Kafka Broker
- Prometheus - metrics collector
- Loki - log storage
- Grafana - metrics visualization

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

- ShutdownHooks => dispose the subscription
- receive vs. receiveAutoAck

## Lessons learned

### Producer

*ProduceSendSource* `producerTemplate.send(source())` is fast - it reaches 250 ops, but it has no automatic support for **backpressure*.
When creating 1000 events per second locally (`./produce.sh ProduceSendSource a8 1ms 10000`) the producer will end in an
error `Could not emit tick 256 due to lack of requests (interval doesn't support small downstream requests that replenish slower than the ticks)`
after the first 256 event are created.

If the Producer produces slower, e.g. with only 200-250 instead of 1000 events per seconds `./produce.sh ProduceSendSource a8 5ms 10000` it works mostly.

```
2023-09-02T15:04:10.136+02:00  INFO 7740 --- [     generate-1] c.g.k.pipeline.service.CounterService    : PROD/-1: ops=200 offset=-1 total=607
2023-09-02T15:04:10.558+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops/p=62 ops=229 offset=12816 total/p=188 total=692
2023-09-02T15:04:10.584+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops/p=53 ops=228 offset=12420 total/p=161 total=697
2023-09-02T15:04:10.615+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops/p=55 ops=228 offset=12758 total/p=169 total=703
2023-09-02T15:04:10.618+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops/p=58 ops=228 offset=12789 total/p=179 total=704
```

**TODO:** How can we use backpressure in the ProduceSendSource code?

*ProduceFlatMap* `source().flatMap(e -> producerTemplate.send(e))` can be called with `./produce.sh ProduceFlatMap a8 1ms 10000`.
It will start creating with 280 ops and slowly reduce the throughput to 65 ops.

```
2023-09-02T15:02:20.810+02:00  INFO 11532 --- [     generate-1] c.g.k.pipeline.service.CounterService    : PROD/-1: ops=63 offset=-1 total=639
2023-09-02T15:02:20.937+02:00  INFO 11532 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops/p=15 ops=67 offset=12164 total/p=145 total=647
2023-09-02T15:02:21.685+02:00  INFO 11532 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops/p=18 ops=66 offset=12530 total/p=188 total=695
2023-09-02T15:02:21.809+02:00  INFO 11532 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops/p=16 ops=66 offset=12514 total/p=176 total=703
2023-09-02T15:02:21.822+02:00  INFO 11532 --- [     generate-1] c.g.k.pipeline.service.CounterService    : PROD/-1: ops=63 offset=-1 total=704
2023-09-02T15:02:21.856+02:00  INFO 11532 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops/p=17 ops=66 offset=12553 total/p=181 total=706
```

### Pipe

With *manual commit* and fast processing: `./pipe.sh PipeReceiveSend a8 b8 pipe-ReceiveSend 0ms newParallel`

```
2023-09-04 01:14:03,428 INFO  [                              parallel-9] TASK/*: ops=39 offset=-1 total=125
2023-09-04 01:14:03,427 INFO  [                              parallel-7] TASK/*: ops=38 offset=-1 total=124
2023-09-04 01:14:03,428 INFO  [                              parallel-8] TASK/*: ops=39 offset=-1 total=126
2023-09-04 01:14:03,428 INFO  [                              parallel-2] TASK/*: ops=39 offset=-1 total=127
2023-09-04 01:14:03,535 INFO  [       reactive-kafka-pipe-ReceiveSend-1] CMMT/1: ops/p=37 ops=37 offset=1576 total/p=121 total=121
2023-09-04 01:14:03,541 INFO  [kafka-producer-network-thread | producer-1] SENT/1: ops/p=38 ops=38 offset=1581 total/p=125 total=125
2023-09-04 01:14:04,509 INFO  [                              parallel-8] TASK/*: ops=38 offset=-1 total=165
2023-09-04 01:14:04,509 INFO  [                              parallel-9] TASK/*: ops=38 offset=-1 total=166
2023-09-04 01:14:04,509 INFO  [                              parallel-7] TASK/*: ops=38 offset=-1 total=164
2023-09-04 01:14:04,510 INFO  [                              parallel-2] TASK/*: ops=39 offset=-1 total=167
2023-09-04 01:14:04,615 INFO  [       reactive-kafka-pipe-ReceiveSend-1] CMMT/1: ops/p=37 ops=37 offset=1616 total/p=161 total=161
2023-09-04 01:14:04,619 INFO  [kafka-producer-network-thread | producer-1] SENT/1: ops/p=38 ops=38 offset=1621 total/p=165 total=165
2023-09-04 01:14:05,166 INFO  [                   newParallelConsumer-1] RECV/1: ops/p=52 ops=52 offset=1710 total/p=257 total=257
```

With *manual commit* and slow processing: `./pipe.sh PipeReceiveSend a8 b8 pipe-ReceiveSend 100ms newParallel 4 1s`

```
2023-09-04 01:11:57,739 INFO  [                              parallel-8] TASK/*: ops=23 offset=-1 total=76
2023-09-04 01:11:57,739 INFO  [                              parallel-2] TASK/*: ops=23 offset=-1 total=76
2023-09-04 01:11:57,739 INFO  [                              parallel-7] TASK/*: ops=23 offset=-1 total=76
2023-09-04 01:11:57,739 INFO  [                              parallel-9] TASK/*: ops=23 offset=-1 total=76
2023-09-04 01:11:57,850 INFO  [kafka-producer-network-thread | producer-1] SENT/1: ops/p=37 ops=37 offset=637 total/p=121 total=121
2023-09-04 01:11:57,851 INFO  [kafka-producer-network-thread | producer-1] CMMT/1: ops/p=37 ops=37 offset=637 total/p=121 total=121
2023-09-04 01:11:58,825 INFO  [                              parallel-9] TASK/*: ops=24 offset=-1 total=105
2023-09-04 01:11:58,825 INFO  [                              parallel-8] TASK/*: ops=24 offset=-1 total=105
2023-09-04 01:11:58,825 INFO  [                              parallel-7] TASK/*: ops=24 offset=-1 total=105
2023-09-04 01:11:58,825 INFO  [                              parallel-2] TASK/*: ops=24 offset=-1 total=105
2023-09-04 01:11:58,938 INFO  [kafka-producer-network-thread | producer-1] SENT/1: ops/p=37 ops=37 offset=679 total/p=161 total=161
2023-09-04 01:11:58,939 INFO  [kafka-producer-network-thread | producer-1] CMMT/1: ops/p=37 ops=37 offset=679 total/p=161 total=161
2023-09-04 01:11:59,588 INFO  [                   newParallelConsumer-1] RECV/1: ops/p=49 ops=49 offset=772 total/p=257 total=257
```

## Older Performance Results

### Producer

- SEND on `kafka-producer-network-thread | producer-1` thread
- PRODUCE on `reactor-kafka-sender-9999999`  thread
- Performance (all local): Total rate with 4 partitions approx. 650 events per second (produce only)
- Performance (DATEV, 1 instance): Total rate with 16 partitions (produce only):
  - ack=all: 270 ops / 16-17 ops/partition
  - ack=1, batchsize=262144, linger=<not-set>: 610 ops
  - ack=1, batchsize=262144: linger=0: 540 ops
  - ack=1, batchsize=16384: 530 ops
  - ack=0, batchsize=262144: linger=<not-set>: 12000 ops
  - ack=1, linger=100: 9 ops (!)
  - ack=1, batchsize=262144, linger=10: 82 ops

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