#!/usr/bin/env bash

export SERVER_PORT=9082
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=pipe
export APPLICATION_MODE=${1:-Pipe}
export APPLICATION_GROUP_ID=${2:-Pipe}
export APPLICATION_PROCESSING_TIME=50ms
export APPLICATION_TOPIC_A=${3:-a-p1}
export APPLICATION_TOPIC_B=${4:-b-p1}

java -jar target/reactor-kafka.jar
