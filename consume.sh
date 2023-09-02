#!/usr/bin/env bash

export SERVER_PORT=9083
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=consume
export APPLICATION_MODE=${1:-Consume}
export APPLICATION_CONSUMER_GROUP_ID=${2:-consume-default}
export APPLICATION_TOPIC_B=${3:-b1}

# Sample only
# export APPLICATION_CONSUMER_MAX_POLL_INTERVAL=PT60S
# export APPLICATION_CONSUMER_MAX_POLL_RECORDS=100

java -jar target/reactor-kafka.jar
