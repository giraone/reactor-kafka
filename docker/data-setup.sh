#!/bin/bash

mkdir -m 777 ${CONTAINER_DATA:-./data}
mkdir -m 777 ${CONTAINER_DATA:-./data}/kafka-1
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1/data
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1/log
mkdir -m 777 ${CONTAINER_DATA:-./data}/redis
mkdir -m 777 ${CONTAINER_DATA:-./data}/redisinsight
mkdir -m 777 ${CONTAINER_DATA:-./data}/prometheus
mkdir -m 777 ${CONTAINER_DATA:-./data}/loki
mkdir -m 777 ${CONTAINER_DATA:-./data}/grafana
