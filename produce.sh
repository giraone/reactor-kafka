#!/usr/bin/env bash

export SERVER_PORT=9081
export LOGGING_LEVEL_COM_GIRAONE=INFO
export APPLICATION_MODE=Produce
export APPLICATION_PRODUCE_INTERVAL=0ms

java -jar target/reactor-kafka.jar
