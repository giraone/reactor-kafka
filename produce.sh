#!/usr/bin/env bash

export SERVER_PORT=9081
export LOGGING_LEVEL_COM_GIRAONE=INFO
export SPRING_APPLICATION_NAME=produce
export APPLICATION_MODE=${1:-Produce}
export APPLICATION_PRODUCE_INTERVAL=100ms
export APPLICATION_TOPIC_A=${2:-a1}

java -jar target/reactor-kafka.jar
