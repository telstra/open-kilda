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

ARG base_image=ubuntu:xenial
FROM ${base_image}

ENV DEBIAN_FRONTEND noninteractive

###############################################
# Install pip and Java8 in base container     #
###############################################

ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV JAVA_MEM_CONFIG "-XX:+PrintFlagsFinal -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"
ENV DEBIAN_FRONTEND noninteractive

COPY app /app

RUN chmod 777 /app/* \
    && apt-get -q -o Acquire::CompressionTypes::Order=bz2 update \
    && apt-get install -yq --no-install-recommends \
        libffi-dev \
        libssl-dev \
        software-properties-common \
        curl \
        wget \
        ssh \
        iputils-ping \
        python \
        python-dev \
        python-pip \
        make \
        apt-transport-https \
        openjdk-8-jdk \
    && pip install --upgrade pip \
    && python -m pip install setuptools \
    && echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64" >> ~/.bashrc \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*
