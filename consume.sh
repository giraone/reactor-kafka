#!/usr/bin/env bash

export SERVER_PORT=9083
export LOGGING_LEVEL_COM_GIRAONE=INFO
export APPLICATION_MODE=Consume

java -jar target/reactor-kafka.jar
