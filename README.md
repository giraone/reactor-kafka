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
mvn -Ploki package
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
When creating 1000 events per second locally (`./produce.sh ProduceSendSource a4 1ms 10000`) the producer will end in an
error `Could not emit tick 256 due to lack of requests (interval doesn't support small downstream requests that replenish slower than the ticks)`
after the first 256 event are created.

If the Producer produces slower, e.g. with only 200-250 instead of 1000 events per seconds `./produce.sh ProduceSendSource a4 5ms 10000` it works mostly.

```
2023-09-02T15:04:10.136+02:00  INFO 7740 --- [     generate-1] c.g.k.pipeline.service.CounterService    : PROD/-1: ops=200 offset=-1 total=607
2023-09-02T15:04:10.558+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/3: ops/p=62 ops=229 offset=12816 total/p=188 total=692
2023-09-02T15:04:10.584+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/0: ops/p=53 ops=228 offset=12420 total/p=161 total=697
2023-09-02T15:04:10.615+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/2: ops/p=55 ops=228 offset=12758 total/p=169 total=703
2023-09-02T15:04:10.618+02:00  INFO 7740 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SEND/1: ops/p=58 ops=228 offset=12789 total/p=179 total=704
```

**TODO:** How can we use backpressure in the ProduceSendSource code?

*ProduceFlatMap* `source().flatMap(e -> producerTemplate.send(e))` can be called with `./produce.sh ProduceFlatMap a4 1ms 10000`.
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

With *manual commit* and fast processing: `./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 0ms false`

```
2023-09-02T15:19:04.784+02:00  INFO 12888 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=5359 total/p=83 total=83
2023-09-02T15:19:04.786+02:00  INFO 12888 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=83
```

Hint: flapMap(concurreny=1 vs concurrency=4) no difference.

With *manual commit* and slow processing: `./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 100ms false`

```
2023-09-02T15:27:11.101+02:00  INFO 136 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=9 ops=9 offset=8878 total/p=72 total=72
2023-09-02T15:27:11.207+02:00  INFO 136 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=71
2023-09-02T15:27:12.191+02:00  INFO 136 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=9 ops=9 offset=8888 total/p=82 total=82
2023-09-02T15:27:12.295+02:00  INFO 136 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=81
```

With *auto commit* and fast processing: `./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 0ms true`

```
2023-09-02T15:25:02.578+02:00  INFO 14608 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=1356 offset=-1 total=10864
2023-09-02T15:25:03.074+02:00  INFO 14608 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=1550 ops=1350 offset=4658 total/p=4659 total=11556
2023-09-02T15:25:03.579+02:00  INFO 14608 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=1381 offset=-1 total=12445
2023-09-02T15:25:03.579+02:00  INFO 14608 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=1381 offset=-1 total=12446
2023-09-02T15:25:04.075+02:00  INFO 14608 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=1615 ops=1398 offset=6470 total/p=6471 total=13368
2023-09-02T15:25:04.580+02:00  INFO 14608 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=1411 offset=-1 total=14128
2023-09-02T15:25:04.581+02:00  INFO 14608 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=1411 offset=-1 total=14129
```

With *auto commit* and slow processing: ./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 100ms true

```
2023-09-02T15:22:55.875+02:00  INFO 8504 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=35 ops=35 offset=5951 total/p=221 total=221
2023-09-02T15:22:56.106+02:00  INFO 8504 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=28 offset=-1 total=179
2023-09-02T15:22:56.106+02:00  INFO 8504 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=28 offset=-1 total=179
2023-09-02T15:22:56.106+02:00  INFO 8504 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=28 offset=-1 total=179
2023-09-02T15:22:56.106+02:00  INFO 8504 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=28 offset=-1 total=177
```

---

With *manual commit* and fast processing: `./pipe.sh PipeSendReceive a4 b4 pipe-SendReceive 0ms false`

```
2023-09-02T15:48:57.302+02:00  INFO 5724 --- [nder-1453062635] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=24 ops=24 offset=512 total/p=513 total=513
2023-09-02T15:48:57.303+02:00  INFO 5724 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=515
2023-09-02T15:48:57.303+02:00  INFO 5724 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=513
2023-09-02T15:48:57.304+02:00  INFO 5724 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=519
2023-09-02T15:48:57.304+02:00  INFO 5724 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=25 offset=-1 total=519
2023-09-02T15:48:57.303+02:00  INFO 5724 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=516
2023-09-02T15:48:57.304+02:00  INFO 5724 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=517
2023-09-02T15:48:57.304+02:00  INFO 5724 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=25 offset=-1 total=520
2023-09-02T15:48:57.303+02:00  INFO 5724 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24 offset=-1 total=514
```

With *auto commit* and fast processing: `./pipe.sh PipeSendReceive a4 b4 pipe-SendReceive 0ms true`

```
2023-09-02T15:50:14.390+02:00  INFO 2856 --- [nder-1453062635] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=590 ops=590 offset=2240 total/p=2241 total=2241
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=590 offset=-1 total=2241
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=589 offset=-1 total=2238
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=590 offset=-1 total=2242
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=589 offset=-1 total=2239
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=591 offset=-1 total=2243
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=591 offset=-1 total=2244
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=590 offset=-1 total=2240
2023-09-02T15:50:14.391+02:00  INFO 2856 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=591 offset=-1 total=2245
```

---

With *manual commit* and fast processing: `./pipe.sh PipePartitioned a4 b4 pipe-PipePartitioned 0ms false`

*all at once!*

```
2023-09-02T15:36:01.521+02:00  INFO 10044 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=24021 offset=-1 total=24166
2023-09-02T15:36:02.169+02:00  INFO 10044 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=6989 ops=27766 offset=11573 total/p=11574 total=45981
2023-09-02T15:36:02.213+02:00  INFO 10044 --- [      worker-10] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=9994 ops=27972 offset=11573 total/p=11574 total=47525
```

With *manual commit* and slow processing: `./pipe.sh PipePartitioned a4 b4 pipe-PipePartitioned 100ms false`

```
2023-09-02T15:38:13.198+02:00  INFO 14368 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=90 total/p=91 total=91
2023-09-02T15:38:13.309+02:00  INFO 14368 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=91
2023-09-02T15:38:14.298+02:00  INFO 14368 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=100 total/p=101 total=101
2023-09-02T15:38:14.409+02:00  INFO 14368 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=101
```

With *manual commit* and medium processing: `./pipe.sh PipePartitioned a4 b4 pipe-PipePartitioned 10ms false`

```
2023-09-02T15:39:01.957+02:00  INFO 964 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=64 ops=64 offset=258 total/p=259 total=259
2023-09-02T15:39:01.987+02:00  INFO 964 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=64 offset=-1 total=260
2023-09-02T15:39:02.972+02:00  INFO 964 --- [       worker-3] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=64 ops=64 offset=323 total/p=324 total=324
2023-09-02T15:39:03.002+02:00  INFO 964 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=64 offset=-1 total=325

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
  - ack=0, batchsize=262144: inger=<not-set>: 12000 ops
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