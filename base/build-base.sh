#!/usr/bin/env bash

pushd base-builder && make && popd
pushd base-java && make && popd
pushd base-maven && make && popd
pushd base-maven/tests && ./project.sh all && popd
