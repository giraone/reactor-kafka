#!/usr/bin/env bash

export SERVER_PORT=9082
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=pipe
export SPRING_PROFILES_ACTIVE=loki
export APPLICATION_LOKI_HOST=localhost
export APPLICATION_LOKI_PORT=3100
export APPLICATION_MODE=${1:-PipeReceiveSend}
export APPLICATION_TOPIC_A=${2:-a1}
export APPLICATION_TOPIC_B=${3:-b1}
export APPLICATION_CONSUMER_GROUP_ID=${4:-pipe-default}
export APPLICATION_PROCESSING_TIME=${5:-50ms}
export APPLICATION_CONSUMER_SCHEDULER_TYPE=${6:-newParallel}
export APPLICATION_CONSUMER_MAX_POLL_RECORDS=${7:-8}
export APPLICATION_CONSUMER_MAX_POLL_INTERVAL=${8:-300s}

java -jar target/reactor-kafka.jar
