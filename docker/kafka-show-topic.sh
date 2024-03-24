#!/bin/bash

if [[ $# -lt 1 ]]; then
  echo "Usage $0  <topic> <broker> <broker-port> "
  echo " e.g. $0 a1"
  echo " e.g. $0 b8 kafka-1 9092"
  exit 1
fi

docker exec -it ${2:-kafka-1} bash -c "kafka-console-consumer \
  --bootstrap-server ${2:-kafka-1}:${3:-9092} \
  --property print.key=true \
  --property print.partition=true \
  --topic ${1} \
  --from-beginning"
