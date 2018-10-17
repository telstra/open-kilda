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
    && apt-get install -y \
        oracle-java8-set-default \
        oracle-java8-installer || ( \
           sed -i \
               -e 's|JAVA_VERSION=8u181|JAVA_VERSION=8u191|' \
               -e 's|PARTNER_URL=http://download.oracle.com/otn-pub/java/jdk/8u181-b13/96a7b8442fe848ef90c96a2fad6ed6d1/|PARTNER_URL=http://download.oracle.com/otn-pub/java/jdk/8u191-b12/2787e4a523244c269598db4e85c51e0c/|' \
               -e 's|SHA256SUM_TGZ="1845567095bfbfebd42ed0d09397939796d05456290fb20a83c476ba09f991d3"|SHA256SUM_TGZ="53c29507e2405a7ffdbba627e6d64856089b094867479edc5ede4105c1da0d65"|' \
               -e 's|J_DIR=jdk1.8.0_181|J_DIR=jdk1.8.0_191|' \
               /var/lib/dpkg/info/oracle-java8-installer.config /var/lib/dpkg/info/oracle-java8-installer.postinst; \
           apt-get install -y) \
    && update-java-alternatives -s java-8-oracle \
    && echo "export JAVA_HOME=${JAVA_HOME}" >> ~/.bashrc \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/oracle-jdk8-installer
