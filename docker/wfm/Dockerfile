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

ARG base_image=kilda/storm:latest
FROM ${base_image}

ENV DEBIAN_FRONTEND noninteractive

RUN \
    pip \
        install \
            kafka && \
    echo "PATH=$PATH:/opt/storm/bin" >> ~/.bashrc

WORKDIR /app
ADD wfm/app /app

# Main config
COPY BUILD/base-storm-topology/resources/topology.properties /app/
RUN grep "blue.green.mode" /app/topology.properties # Debug to check that 'stable' images use 'blue', 'latest' - 'green'



#connecteddevices
COPY BUILD/connecteddevices-storm-topology/libs/*               /app/connecteddevices-storm-topology/libs/
COPY BUILD/connecteddevices-storm-topology/build.gradle         /app/connecteddevices-storm-topology/
COPY BUILD/connecteddevices-storm-topology/resources/connecteddevices-topology.yaml	    /app/connecteddevices-storm-topology/topology-definition.yaml



#floodlightrouter
COPY BUILD/floodlightrouter-storm-topology/libs/*               /app/floodlightrouter-storm-topology/libs/
COPY BUILD/floodlightrouter-storm-topology/build.gradle         /app/floodlightrouter-storm-topology/
COPY BUILD/floodlightrouter-storm-topology/resources/floodlightrouter-topology.yaml	    /app/floodlightrouter-storm-topology/topology-definition.yaml



#flowhs
COPY BUILD/flowhs-storm-topology/libs/*                         /app/flowhs-storm-topology/libs/
COPY BUILD/flowhs-storm-topology/build.gradle                   /app/flowhs-storm-topology/
COPY BUILD/flowhs-storm-topology/resources/flowhs-topology.yaml	    /app/flowhs-storm-topology/topology-definition.yaml



#flowmonitoring
COPY BUILD/flowmonitoring-storm-topology/libs/*                 /app/flowmonitoring-storm-topology/libs/
COPY BUILD/flowmonitoring-storm-topology/build.gradle           /app/flowmonitoring-storm-topology/
COPY BUILD/flowmonitoring-storm-topology/resources/flowmonitoring-topology.yaml	    /app/flowmonitoring-storm-topology/topology-definition.yaml


#history
COPY BUILD/history-storm-topology/libs/*                        /app/history-storm-topology/libs/
COPY BUILD/history-storm-topology/build.gradle                  /app/history-storm-topology/
COPY BUILD/history-storm-topology/resources/history-topology.yaml	    /app/history-storm-topology/topology-definition.yaml


#isllatency
COPY BUILD/isllatency-storm-topology/libs/*                     /app/isllatency-storm-topology/libs/
COPY BUILD/isllatency-storm-topology/build.gradle               /app/isllatency-storm-topology/
COPY BUILD/isllatency-storm-topology/resources/isllatency-topology.yaml	    /app/isllatency-storm-topology/topology-definition.yaml



#nbworker
COPY BUILD/nbworker-storm-topology/libs/*                       /app/nbworker-storm-topology/libs/
COPY BUILD/nbworker-storm-topology/build.gradle                 /app/nbworker-storm-topology/
COPY BUILD/nbworker-storm-topology/resources/nbworker-topology.yaml	    /app/nbworker-storm-topology/topology-definition.yaml



#network
COPY BUILD/network-storm-topology/libs/*                        /app/network-storm-topology/libs/
COPY BUILD/network-storm-topology/build.gradle                  /app/network-storm-topology/
COPY BUILD/network-storm-topology/resources/network-topology.yaml	    /app/network-storm-topology/topology-definition.yaml



#opentsdb
COPY BUILD/opentsdb-storm-topology/libs/*                       /app/opentsdb-storm-topology/libs/
COPY BUILD/opentsdb-storm-topology/build.gradle                 /app/opentsdb-storm-topology/
COPY BUILD/opentsdb-storm-topology/resources/opentsdb-topology.yaml	    /app/opentsdb-storm-topology/topology-definition.yaml



#ping
COPY BUILD/ping-storm-topology/libs/*                           /app/ping-storm-topology/libs/
COPY BUILD/ping-storm-topology/build.gradle                     /app/ping-storm-topology/
COPY BUILD/ping-storm-topology/resources/ping-topology.yaml	    /app/ping-storm-topology/topology-definition.yaml



#portstate
COPY BUILD/portstate-storm-topology/libs/*                      /app/portstate-storm-topology/libs/
COPY BUILD/portstate-storm-topology/build.gradle                /app/portstate-storm-topology/
COPY BUILD/portstate-storm-topology/resources/portstate-topology.yaml	    /app/portstate-storm-topology/topology-definition.yaml



#reroute
COPY BUILD/reroute-storm-topology/libs/*                        /app/reroute-storm-topology/libs/
COPY BUILD/reroute-storm-topology/build.gradle                  /app/reroute-storm-topology/
COPY BUILD/reroute-storm-topology/resources/reroute-topology.yaml	    /app/reroute-storm-topology/topology-definition.yaml



#stats
COPY BUILD/stats-storm-topology/libs/*                          /app/stats-storm-topology/libs/
COPY BUILD/stats-storm-topology/build.gradle                    /app/stats-storm-topology/
COPY BUILD/stats-storm-topology/resources/stats-topology.yaml	    /app/stats-storm-topology/topology-definition.yaml



#swmanager
COPY BUILD/swmanager-storm-topology/libs/*                      /app/swmanager-storm-topology/libs/
COPY BUILD/swmanager-storm-topology/build.gradle                /app/swmanager-storm-topology/
COPY BUILD/swmanager-storm-topology/resources/swmanager-topology.yaml	    /app/swmanager-storm-topology/topology-definition.yaml


#server42 control
COPY BUILD/server42-control-storm-topology/libs/*               /app/server42-control-storm-topology/libs/
COPY BUILD/server42-control-storm-topology/build.gradle         /app/server42-control-storm-topology/
COPY BUILD/server42-control-storm-topology/resources/control-topology.yaml	    /app/server42-control-storm-topology/topology-definition.yaml


CMD /app/entry-point.sh

RUN TZ=Australia/Melbourne date >> /container_baked_on.txt
