#!/bin/bash

for topic in topic-1 topic-2
do
  echo "Create $topic"
  docker exec -it kafka-1 kafka-topics \
    --bootstrap-server kafka-1:9092 \
    --create \
    --topic $topic \
    --replication-factor 1 \
    --partitions 4
done

# kafka-configs --alter --topic job-accepted --add-config "cleanup.policy=compact" --add-config "delete.retention.ms=10000" --bootstrap-server kafka-1:9092
# kafka-topics --describe --topic job-accepted --bootstrap-server kafka-1:9092
