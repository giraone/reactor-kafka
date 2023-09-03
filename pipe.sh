#!/usr/bin/env bash

export SERVER_PORT=9082
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=pipe
export APPLICATION_MODE=${1:-PipeReceiveSend}
export APPLICATION_TOPIC_A=${2:-a1}
export APPLICATION_TOPIC_B=${3:-b1}
export APPLICATION_CONSUMER_GROUP_ID=${4:-pipe-default}
export APPLICATION_PROCESSING_TIME=${5:-50ms}
export APPLICATION_CONSUMER_SCHEDULER_TYPE=${6:-newElastic}
export APPLICATION_CONSUMER_AUTO_COMMIT=${7:-false}

java -jar target/reactor-kafka.jar
