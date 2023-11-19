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
- Producer test are disabled because flaky

## Lessons learned

### Producer

**Cloud Foundry with central Kafka cluster**

*ProduceConcatMap* `sourceHot().concatMap(e -> producerTemplate.send(e))` reaches 270 ops on a 16 partition topic.
*ProduceFlatMap* `sourceHot().flatMap(e -> producerTemplate.send(e), 1, 1)` reaches 280 ops on a 16 partition topic.
*ProduceFlatMap* `sourceHot().flatMap(e -> producerTemplate.send(e))` reaches 1020 ops on a 16 partition topic.
*ProduceSendSource* `producerTemplate.send(sourceHot())` reaches 300 ops on a 16 partition topic.

**Local Docker with Single Kafka Broker**

| Mode              | Prod-Interval | Partitions |  Plaform / Inst / Conc |  numberOfEvents |  totalSec | OPS |
|:------------------|--------------:|-----------:|-----------------------:|----------------:|----------:|----:|
| ProduceFlatMap    |         1 ms  |          8 | Docker (local) / 1 / 8 |           10000 |       155 |  64 |
| ProduceConcatMap  |         1 ms  |          8 | Docker (local) / 1 / 8 |           10000 |       155 |  64 |
| ProduceSendSource |         1 ms  |          8 | Docker (local) / 1 / 8 |           10000 |       155 |  64 |

### Pipe

| Mode             | Proc-Time |          Partitions | Inst / Conc |       Scheduler | maxPollRec/Int | OPS |
|:-----------------|----------:|--------------------:|------------:|----------------:|---------------:|----:|
| PipeReceiveSend  |     10 ms |                  16 |      1 / 16 |     newParallel |        16 / 1s | 170 |
| PipeReceiveSend  |     10 ms |                  16 |      1 / 16 |     newParallel |        16 / 5s | 171 |
| PipeReceiveSend  |     10 ms |                  16 |      1 / 16 |     newParallel |        32 / 5s | 135 |
| PipeReceiveSend  |     10 ms |                  16 |      1 / 16 |     newParallel |        64 /20s |  26 |

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