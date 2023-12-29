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

ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ENV PACKAGE apache-zookeeper-3.9.0

WORKDIR /tmp/

RUN    wget -q https://archive.apache.org/dist/zookeeper/zookeeper-3.9.0/$PACKAGE-bin.tar.gz \
    && wget -q https://archive.apache.org/dist/zookeeper/zookeeper-3.9.0/$PACKAGE-bin.tar.gz.sha512 \
    && sha512sum -c $PACKAGE-bin.tar.gz.sha512 \
    && tar -xzf $PACKAGE-bin.tar.gz --directory /opt/ \
    && ln -s /opt/$PACKAGE-bin /opt/zookeeper \
    && rm -rfv /tmp/*

WORKDIR /opt/zookeeper/

COPY zookeeper-conf/zoo.cfg zookeeper-conf/log4j.properties /opt/zookeeper/conf/
