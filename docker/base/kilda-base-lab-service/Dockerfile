# Copyright 2018 Telstra Open Source
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
ARG base_image=ubuntu:focal
FROM ${base_image}

ENV DEBIAN_FRONTEND noninteractive

# setuptools, wheel and tox should be installed one by one otherwise it is going to fail with no good reason
RUN apt update \
    && apt install -y --no-install-recommends \
        software-properties-common \
        curl \
        make \
        python3-pip \
        iproute2 \
        apt-transport-https \
        net-tools \
        iperf3 \
        iputils-ping \
        openvswitch-switch \
        openvswitch-common \
        gcc \
        build-essential \
        automake \
    && pip3 install setuptools==45.2.0 \
    && pip3 install wheel==0.34.2 \
    && pip3 install tox==3.14.4 \
    && cd /root \
    && curl  https://www.openvswitch.org/releases/openvswitch-2.14.0.tar.gz -o openvswitch-2.14.0.tar.gz \
    && tar -xvf openvswitch-2.14.0.tar.gz \
    && cd  openvswitch-2.14.0 \
    && cp ofproto/ofproto-dpif.h ofproto/ofproto-dpif.h.original \
    && sed  -i  's/enum { N_TABLES = 255 };/enum { N_TABLES = 11 };/' ofproto/ofproto-dpif.h \
    && ./configure --prefix=/usr --localstatedir=/var --sysconfdir=/etc \
    && make \
    && make install \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*

ADD merged-requirements.txt /
RUN pip3 install -r merged-requirements.txt
