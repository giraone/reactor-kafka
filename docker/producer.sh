#!/bin/bash

msg="$1"
topic=${2:-a-p1}

echo "$msg" | docker exec -i kafka-1 kafka-console-producer \
    --bootstrap-server kafka-1:9092 \
    --property parse.key=true \
    --property key.separator=: \
    --topic $topic
