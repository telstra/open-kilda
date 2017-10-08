#!/usr/bin/env bash
# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#


pushd base-builder && make && popd
pushd base-java && make && popd
pushd base-maven && make && popd
pushd base-maven/tests && ./project.sh all && popd
docker build -t kilda/base-ubuntu:latest kilda-base-ubuntu/
docker build -t kilda/base-floodlight:latest base-floodlight/
