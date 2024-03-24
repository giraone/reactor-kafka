#!/bin/bash

docker rmi reactor-kafka
mvn -ntp verify -DskipTests -Ploki jib:dockerBuild