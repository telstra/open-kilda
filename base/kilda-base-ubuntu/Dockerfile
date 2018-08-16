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
# Install Ansible and Java8 in base container #
###############################################

RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
RUN echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc

RUN apt-get update \
    && apt-get autoremove \
    && apt-get install -y \
        build-essential \
        apt-utils \
        libffi-dev \
        libssl-dev \
        software-properties-common \
        curl \
        ssh \
        iputils-ping \
        python \
        python-dev \
        python-pip \
    && pip install --upgrade pip \
    && python -m pip install \
        ansible \
    && add-apt-repository ppa:webupd8team/java \
    && apt-get update \
    && apt-get install -y \
        oracle-java8-installer || ( \
           sed -i \
               -e 's|JAVA_VERSION=8u171|JAVA_VERSION=8u181|' \
               -e 's|PARTNER_URL=http://download.oracle.com/otn-pub/java/jdk/8u171-b11/512cd62ec5174c3487ac17c61aaa89e8/|PARTNER_URL=http://download.oracle.com/otn-pub/java/jdk/8u181-b13/96a7b8442fe848ef90c96a2fad6ed6d1/|' \
               -e 's|SHA256SUM_TGZ="b6dd2837efaaec4109b36cfbb94a774db100029f98b0d78be68c27bec0275982"|SHA256SUM_TGZ="1845567095bfbfebd42ed0d09397939796d05456290fb20a83c476ba09f991d3"|' \
               -e 's|J_DIR=jdk1.8.0_171|J_DIR=jdk1.8.0_181|' \
               /var/lib/dpkg/info/oracle-java8-installer.config /var/lib/dpkg/info/oracle-java8-installer.postinst; \
           apt-get install -y) \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN chmod +x /usr/local/bin/ansible*
RUN mkdir /etc/ansible
#Sets the default context of Ansible to be the local container
ADD config/hosts /etc/ansible/hosts
ADD app /app
RUN chmod 777 /app/*
