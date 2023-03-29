#!/usr/bin/env bash

export APPLICATION_MODE=consume
export SERVER_PORT=8082
java -jar target/reactor-kafka.jar
