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

FROM kilda/base-ubuntu

ENV DEBIAN_FRONTEND noninteractive
ENV MININET_REPO https://github.com/mininet/mininet.git
ENV MININET_INSTALLER mininet/util/install.sh
ENV INSTALLER_SWITCHES -fbinptvwyx

# Adding openvswitch-testcontroller to facilitate running examples with embedded controller
RUN apt-get update \
    && apt-get install -qy \
        net-tools \
        tcpdump \
        inetutils-ping \
        ca-certificates \
        openssh-client \
        python-scapy \
        mininet \
        vlan \
        openvswitch-testcontroller \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

RUN ln -s /usr/bin/ovs-testcontroller /usr/bin/ovs-controller
RUN ln -s /usr/bin/ovs-testcontroller /usr/bin/controller
RUN ln -s /usr/sbin/tcpdump /usr/bin/tcpdump
RUN pip install python-logstash requests

ADD app /app
RUN chmod a+rx /app/entry-point.sh

ADD dist /py-deps
RUN pip install "$(echo /py-deps/kilda_mininet-*-none-any.whl | tail -n1)"

WORKDIR /tmp

VOLUME ["/data"]

WORKDIR /data

# Default command.
CMD ["/app/entry-point.sh"]
