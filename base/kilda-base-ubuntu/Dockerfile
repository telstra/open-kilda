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

FROM ubuntu:xenial

ENV DEBIAN_FRONTEND noninteractive

###############################################
# Install pip and Java8 in base container     #
###############################################

ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

COPY app /app

RUN chmod 777 /app/* \
    && echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections \
    && echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections \
    && apt -o Acquire::CompressionTypes::Order=bz2 update \
    && apt install -y --no-install-recommends \
        libffi-dev \
        libssl-dev \
        software-properties-common \
        curl \
        ssh \
        iputils-ping \
        python \
        python-dev \
        python-pip \
        make \
        apt-transport-https \
    && pip install --upgrade pip \
    && python -m pip install setuptools \
    && add-apt-repository ppa:webupd8team/java \
    && apt update \
    && apt install -y --no-install-recommends \
        oracle-java8-installer \
        oracle-java8-set-default \
    && update-java-alternatives -s java-8-oracle \
    && echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/oracle-jdk8-installer
