#!/bin/bash

mkdir -m 777 ${CONTAINER_DATA:-./data}
mkdir -m 777 ${CONTAINER_DATA:-./data}/kafka-1
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1/data
mkdir -m 777 ${CONTAINER_DATA:-./data}/zk-1/log
