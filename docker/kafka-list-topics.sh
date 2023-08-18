#!/bin/bash

docker exec ${1:-kafka-1} kafka-topics \
    --bootstrap-server ${1:-kafka-1}:${2:-9092} \
    --list > _topics

cat _topics

cat _topics | grep -v __ | while read topic; do
  docker exec --tty ${1:-kafka-1} kafka-topics \
      --bootstrap-server ${1:-kafka-1}:${2:-9092} \
      --describe --topic $topic
done
