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

ARG OVS_VERSION
# By default OVS version doesn't support vxlan flows. This could be overrided via `build-arg`, see makefile.tmpl.
ENV OVS_VERSION=${OVS_VERSION:-"kilda.v2.15.0.1"}

ENV DEBIAN_FRONTEND noninteractive

# setuptools, wheel and tox should be installed one by one otherwise it is going to fail with no good reason
RUN apt update \
    && apt install -y --no-install-recommends \
        software-properties-common \
        wget \
        make \
        python3-pip \
        iproute2 \
        apt-transport-https \
        net-tools \
        iperf3 \
        iputils-ping \
        libtool-bin \
        gcc \
        build-essential \
        automake \
    && pip3 install setuptools==45.2.0 \
    && pip3 install wheel==0.34.2 \
    && pip3 install tox==3.14.4 \
    && cd /root \
    && wget https://github.com/kilda/ovs/archive/refs/tags/${OVS_VERSION}.tar.gz \
    && tar -xvf ${OVS_VERSION}.tar.gz \
    && cd ovs-${OVS_VERSION} \
    && ./boot.sh \
    && ./configure --prefix=/usr --localstatedir=/var --sysconfdir=/etc \
    && make \
    && make install \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*

ADD merged-requirements.txt /
RUN pip3 install -r merged-requirements.txt
