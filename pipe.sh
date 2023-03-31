#!/usr/bin/env bash

export SERVER_PORT=9082
export LOGGING_LEVEL_COM_GIRAONE=INFO
export APPLICATION_MODE=${1:-PipePartitioned}
export APPLICATION_TRANSFORM_INTERVAL=0ms

java -jar target/reactor-kafka.jar
