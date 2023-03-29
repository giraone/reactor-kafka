#!/usr/bin/env bash

export APPLICATION_MODE=pipeline
export SERVER_PORT=9082
java -jar target/reactor-kafka.jar
