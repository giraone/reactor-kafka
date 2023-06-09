#!/bin/bash

topic=${1:-topic-2}

docker exec kafka-1 kafka-console-consumer \
    --bootstrap-server kafka-1:9092 \
    --property print.key=true \
    --topic $topic \
    --from-beginning
