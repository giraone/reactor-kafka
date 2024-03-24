#!/bin/bash

for topic in a1 b1 c1
do
  echo "Create $topic"
  docker exec -it kafka-1 kafka-topics \
    --bootstrap-server kafka-1:9092 \
    --create \
    --topic $topic \
    --replication-factor 1 \
    --partitions 1
done

for topic in a8 b8 c8
do
  echo "Create $topic"
  docker exec -it kafka-1 kafka-topics \
    --bootstrap-server kafka-1:9092 \
    --create \
    --topic $topic \
    --replication-factor 1 \
    --partitions 8
done

# kafka-configs --alter --topic a1 --add-config "cleanup.policy=compact" --add-config "delete.retention.ms=10000" --bootstrap-server kafka-1:9092
# kafka-topics --describe --topic a1 --bootstrap-server kafka-1:9092
