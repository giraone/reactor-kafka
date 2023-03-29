#!/usr/bin/env bash

export APPLICATION_MODE=produce
export SERVER_PORT=8081
java -jar target/reactor-kafka.jar
