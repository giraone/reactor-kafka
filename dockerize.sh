#!/bin/bash

docker rmi reactor-kafka
mvn -ntp verify -DskipTests jib:dockerBuild