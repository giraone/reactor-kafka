#!/bin/bash

rm -rf ${CONTAINER_DATA:-./data}/kafka-1
rm -rf ${CONTAINER_DATA:-./data}/zk-1
rm -rf ${CONTAINER_DATA:-./data}/redis
rm -rf ${CONTAINER_DATA:-./data}/redisinsight
rm -rf ${CONTAINER_DATA:-./data}/prometheus
rm -rf ${CONTAINER_DATA:-./data}/loki
rm -rf ${CONTAINER_DATA:-./data}/grafana