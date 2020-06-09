#!/usr/bin/env bash

[ ! -z $(docker images -q kilda/server42dpdk-base:latest) ] || docker build -t kilda/server42dpdk-base:latest . -f Dockerfile.prebuild

docker build -t kilda/server42dpdk:latest . -f Dockerfile
