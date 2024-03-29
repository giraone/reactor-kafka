#!/bin/bash

topic=${1:-b1}

docker exec kafka-1 kafka-console-consumer \
    --bootstrap-server kafka-1:9092 \
    --property print.key=true \
    --topic $topic \
    --from-beginning
