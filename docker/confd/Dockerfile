# Copyright 2021 Telstra Open Source
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

ARG base_image=ubuntu:20.04
FROM ${base_image}

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get -q -o Acquire::CompressionTypes::Order=bz2 update \
    && apt-get install -yq --no-install-recommends \
        wget \
        ca-certificates \
    && wget https://github.com/kelseyhightower/confd/releases/download/v0.16.0/confd-0.16.0-linux-amd64 -O /usr/local/bin/confd \
    && chmod +x /usr/local/bin/confd \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*
