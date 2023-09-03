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

With *manual commit* and fast processing: `./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 0ms newParallel`

```
2023-09-03T17:55:59.488+02:00  INFO 2432 --- [e-ReceiveSend-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=40 ops=40 offset=9940 total/p=41 total=41
2023-09-03T17:55:59.492+02:00  INFO 2432 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=44 offset=-1 total=49
2023-09-03T17:55:59.494+02:00  INFO 2432 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=47 offset=-1 total=52
2023-09-03T17:55:59.492+02:00  INFO 2432 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=45 offset=-1 total=50
2023-09-03T17:55:59.493+02:00  INFO 2432 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=46 offset=-1 total=51
2023-09-03T17:56:00.585+02:00  INFO 2432 --- [e-ReceiveSend-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=38 ops=38 offset=9980 total/p=81 total=81
2023-09-03T17:56:00.589+02:00  INFO 2432 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=40 offset=-1 total=89
2023-09-03T17:56:00.589+02:00  INFO 2432 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=40 offset=-1 total=90
2023-09-03T17:56:00.589+02:00  INFO 2432 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=91
2023-09-03T17:56:00.590+02:00  INFO 2432 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=92
2023-09-03T17:56:01.667+02:00  INFO 2432 --- [e-ReceiveSend-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=37 ops=37 offset=10020 total/p=121 total=121
2023-09-03T17:56:01.670+02:00  INFO 2432 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=39 offset=-1 total=129
2023-09-03T17:56:01.671+02:00  INFO 2432 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=40 offset=-1 total=132
2023-09-03T17:56:01.670+02:00  INFO 2432 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=39 offset=-1 total=130
2023-09-03T17:56:01.671+02:00  INFO 2432 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=39 offset=-1 total=131
2023-09-03T17:56:02.752+02:00  INFO 2432 --- [e-ReceiveSend-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=37 ops=37 offset=10060 total/p=161 total=161
2023-09-03T17:56:02.752+02:00  INFO 2432 --- [     parallel-3] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=38 offset=-1 total=169
2023-09-03T17:56:02.752+02:00  INFO 2432 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=38 offset=-1 total=170
2023-09-03T17:56:02.752+02:00  INFO 2432 --- [     parallel-5] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=39 offset=-1 total=171
2023-09-03T17:56:02.752+02:00  INFO 2432 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=39 offset=-1 total=172
2023-09-03T17:56:03.301+02:00  INFO 2432 --- [allelConsumer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=52 ops=52 offset=10154 total/p=257 total=257
```

With *manual commit* and slow processing: `./pipe.sh PipeReceiveSend a4 b4 pipe-ReceiveSend 100ms newParallel`

```
2023-09-03T17:58:37.634+02:00  INFO 11032 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=18 offset=-1 total=19
2023-09-03T17:58:37.634+02:00  INFO 11032 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=18 offset=-1 total=19
2023-09-03T17:58:37.634+02:00  INFO 11032 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=18 offset=-1 total=19
2023-09-03T17:58:37.634+02:00  INFO 11032 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=19 offset=-1 total=20
2023-09-03T17:58:37.748+02:00  INFO 11032 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=38 ops=38 offset=10248 total/p=41 total=41
2023-09-03T17:58:38.730+02:00  INFO 11032 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=17 offset=-1 total=38
2023-09-03T17:58:38.730+02:00  INFO 11032 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=16 offset=-1 total=36
2023-09-03T17:58:38.730+02:00  INFO 11032 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=17 offset=-1 total=37
2023-09-03T17:58:38.730+02:00  INFO 11032 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=16 offset=-1 total=35
2023-09-03T17:58:38.849+02:00  INFO 11032 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=37 ops=37 offset=10287 total/p=81 total=81
2023-09-03T17:58:39.815+02:00  INFO 11032 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=22 offset=-1 total=72
2023-09-03T17:58:39.815+02:00  INFO 11032 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=22 offset=-1 total=72
2023-09-03T17:58:39.815+02:00  INFO 11032 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=22 offset=-1 total=72
2023-09-03T17:58:39.815+02:00  INFO 11032 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=22 offset=-1 total=72
2023-09-03T17:58:39.928+02:00  INFO 11032 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=37 ops=37 offset=10328 total/p=121 total=121
2023-09-03T17:58:40.905+02:00  INFO 11032 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=23 offset=-1 total=100
2023-09-03T17:58:40.905+02:00  INFO 11032 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=23 offset=-1 total=100
2023-09-03T17:58:40.905+02:00  INFO 11032 --- [     parallel-9] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=23 offset=-1 total=100
2023-09-03T17:58:40.905+02:00  INFO 11032 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=23 offset=-1 total=100
2023-09-03T17:58:41.017+02:00  INFO 11032 --- [ad | producer-1] c.g.k.pipeline.service.CounterService    : SENT/2: ops/p=37 ops=37 offset=10366 total/p=161 total=161
2023-09-03T17:58:41.672+02:00  INFO 11032 --- [allelConsumer-1] c.g.k.pipeline.service.CounterService    : RECV/2: ops/p=49 ops=49 offset=10461 total/p=257 total=257
```

---

With *manual commit* and fast processing: `./pipe.sh PipePartitioned a4 b4 pipe-PipePartitioned 0ms newElastic`

```
2023-09-03T18:01:17.797+02:00  INFO 11124 --- [pePartitioned-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=62
2023-09-03T18:01:17.797+02:00  INFO 11124 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=62 total/p=63 total=63
2023-09-03T18:01:18.894+02:00  INFO 11124 --- [pePartitioned-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=72
2023-09-03T18:01:18.895+02:00  INFO 11124 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=72 total/p=73 total=73
2023-09-03T18:01:19.985+02:00  INFO 11124 --- [pePartitioned-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=82
2023-09-03T18:01:19.987+02:00  INFO 11124 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=82 total/p=83 total=83
2023-09-03T18:01:21.079+02:00  INFO 11124 --- [pePartitioned-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=92
2023-09-03T18:01:21.080+02:00  INFO 11124 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=92 total/p=93 total=93
2023-09-03T18:01:22.186+02:00  INFO 11124 --- [pePartitioned-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=102
2023-09-03T18:01:22.188+02:00  INFO 11124 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=102 total/p=103 total=103
```

With *manual commit* and slow processing: `./pipe.sh PipePartitioned a4 b4 pipe-PipePartitioned 100ms newElastic`

```
2023-09-03T18:02:15.237+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=159 total/p=10 total=10
2023-09-03T18:02:15.346+02:00  INFO 7992 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=10
2023-09-03T18:02:16.327+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=169 total/p=20 total=20
2023-09-03T18:02:16.437+02:00  INFO 7992 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=20
2023-09-03T18:02:17.431+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=179 total/p=30 total=30
2023-09-03T18:02:17.541+02:00  INFO 7992 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=30
2023-09-03T18:02:18.531+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=189 total/p=40 total=40
2023-09-03T18:02:18.641+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=40
2023-09-03T18:02:19.637+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=199 total/p=50 total=50
2023-09-03T18:02:19.748+02:00  INFO 7992 --- [     parallel-4] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=50
2023-09-03T18:02:20.736+02:00  INFO 7992 --- [     parallel-2] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=9 ops=9 offset=209 total/p=60 total=60
2023-09-03T18:02:20.846+02:00  INFO 7992 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=9 offset=-1 total=60
```

With *manual commit* and fast processing: `./pipe.sh PipeSendReceive a4 b4 pipe-SendReceive 0ms newElastic`

```
2023-09-03T18:04:12.241+02:00  INFO 7960 --- [ender-703555670] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=50 ops=50 offset=10599 total/p=261 total=261
2023-09-03T18:04:12.243+02:00  INFO 7960 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=50 offset=-1 total=260
2023-09-03T18:04:12.243+02:00  INFO 7960 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=51 offset=-1 total=263
2023-09-03T18:04:12.243+02:00  INFO 7960 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=51 offset=-1 total=262
2023-09-03T18:04:12.243+02:00  INFO 7960 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=50 offset=-1 total=261
2023-09-03T18:04:17.489+02:00  INFO 7960 --- [ender-703555670] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=43 ops=43 offset=10791 total/p=453 total=453
2023-09-03T18:04:17.491+02:00  INFO 7960 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=43 offset=-1 total=452
2023-09-03T18:04:17.491+02:00  INFO 7960 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=43 offset=-1 total=450
2023-09-03T18:04:17.491+02:00  INFO 7960 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=43 offset=-1 total=451
2023-09-03T18:04:17.491+02:00  INFO 7960 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=43 offset=-1 total=453
2023-09-03T18:04:22.636+02:00  INFO 7960 --- [ender-703555670] c.g.k.pipeline.service.CounterService    : RECV/3: ops/p=41 ops=41 offset=10983 total/p=645 total=645
2023-09-03T18:04:22.636+02:00  INFO 7960 --- [     parallel-6] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=641
2023-09-03T18:04:22.636+02:00  INFO 7960 --- [     parallel-7] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=642
2023-09-03T18:04:22.637+02:00  INFO 7960 --- [     parallel-8] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=643
2023-09-03T18:04:22.637+02:00  INFO 7960 --- [     parallel-1] c.g.k.pipeline.service.CounterService    : TASK/-1: ops=41 offset=-1 total=644
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