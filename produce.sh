#!/usr/bin/env bash

export SERVER_PORT=9081
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=produce
export APPLICATION_MODE=${1:-ProduceSendSource}
export APPLICATION_TOPIC_A=${2:-a1}
export APPLICATION_PRODUCE_INTERVAL=${3:-100ms}
export APPLICATION_PRODUCER_VARIABLES_MAX_NUMBER_OF_EVENTS=${4:-1000000}

java -jar target/reactor-kafka.jar
