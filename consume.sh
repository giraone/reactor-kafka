#!/usr/bin/env bash

export SERVER_PORT=9083
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=consume
export APPLICATION_MODE=${1:-Consume}
export APPLICATION_GROUP_ID=${2:-Consume}
export APPLICATION_TOPIC_B=${3:-b-p1}

java -jar target/reactor-kafka.jar
