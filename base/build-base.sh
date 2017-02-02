#!/usr/bin/env bash

pushd base-builder && make && popd
pushd base-java && make && popd
pushd base-maven && make && popd
pushd base-maven/tests && ./project.sh all && popd
docker build -t kilda/base-ubuntu:latest kilda-base-ubuntu/
docker build -t kilda/base-floodlight:latest base-floodlight/
