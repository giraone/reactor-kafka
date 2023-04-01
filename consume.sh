#!/usr/bin/env bash

export SERVER_PORT=9083
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=consume
export APPLICATION_MODE=${1:-Consume}
export SPRING_KAFKA_CONSUMER_GROUP_ID=${1:-Consume}

java -jar target/reactor-kafka.jar
