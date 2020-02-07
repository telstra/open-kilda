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

RUN pip install kafka
RUN echo "PATH=$PATH:/opt/storm/bin" >> ~/.bashrc

WORKDIR /app
ADD docker/wfm/app /app
#ADD src/main/resources/topology.properties /app/

# Main config
COPY src-java/base-topology/base-storm-topology/src/release/resources/topology.properties /app/



#connecteddevices
COPY src-java/connecteddevices-topology/connecteddevices-storm-topology/build/libs/*                            /app/connecteddevices-storm-topology/libs/
#COPY src-java/connecteddevices-topology/connecteddevices-storm-topology/build/dependency-jars/*                 /app/connecteddevices-storm-topology/dependency-jars/
COPY src-java/connecteddevices-topology/connecteddevices-storm-topology/build.gradle                            /app/connecteddevices-storm-topology/



#floodlightrouter
COPY src-java/floodlightrouter-topology/floodlightrouter-storm-topology/build/libs/*                            /app/floodlightrouter-storm-topology/libs/
#COPY src-java/floodlightrouter-topology/floodlightrouter-storm-topology/build/dependency-jars/*                 /app/floodlightrouter-storm-topology/dependency-jars/
COPY src-java/floodlightrouter-topology/floodlightrouter-storm-topology/build.gradle                            /app/floodlightrouter-storm-topology/



#flow
COPY src-java/flow-topology/flow-storm-topology/build/libs/*                                                    /app/flow-storm-topology/libs/
#COPY src-java/flow-topology/flow-storm-topology/build/dependency-jars/*                                         /app/flow-storm-topology/dependency-jars/
COPY src-java/flow-topology/flow-storm-topology/build.gradle                                                    /app/flow-storm-topology/



#flowhs
COPY src-java/flowhs-topology/flowhs-storm-topology/build/libs/*                                                /app/flowhs-storm-topology/libs/
#COPY src-java/flowhs-topology/flowhs-storm-topology/build/dependency-jars/*                                     /app/flowhs-storm-topology/dependency-jars/
COPY src-java/flowhs-topology/flowhs-storm-topology/build.gradle                                                /app/flowhs-storm-topology/



#isllatency
COPY src-java/isllatency-topology/isllatency-storm-topology/build/libs/*                                       /app/isllatency-storm-topology/libs/
#COPY src-java/isllatency-topology/isllatency-storm-topology/build/dependency-jars/*                            /app/isllatency-storm-topology/dependency-jars/
COPY src-java/isllatency-topology/isllatency-storm-topology/build.gradle                                       /app/isllatency-storm-topology/



#nbworker
COPY src-java/nbworker-topology/nbworker-storm-topology/build/libs/*                                            /app/nbworker-storm-topology/libs/
#COPY src-java/nbworker-topology/nbworker-storm-topology/build/dependency-jars/*                                 /app/nbworker-storm-topology/dependency-jars/
COPY src-java/nbworker-topology/nbworker-storm-topology/build.gradle                                            /app/nbworker-storm-topology/



#network
COPY src-java/network-topology/network-storm-topology/build/libs/*                                              /app/network-storm-topology/libs/
#COPY src-java/network-topology/network-storm-topology/build/dependency-jars/*                                   /app/network-storm-topology/dependency-jars/
COPY src-java/network-topology/network-storm-topology/build.gradle                                              /app/network-storm-topology/



#opentsdb
COPY src-java/opentsdb-topology/opentsdb-storm-topology/build/libs/*                                            /app/opentsdb-storm-topology/libs/
#COPY src-java/opentsdb-topology/opentsdb-storm-topology/build/dependency-jars/*                                 /app/opentsdb-storm-topology/dependency-jars/
COPY src-java/opentsdb-topology/opentsdb-storm-topology/build.gradle                                            /app/opentsdb-storm-topology/



#ping
COPY src-java/ping-topology/ping-storm-topology/build/libs/*                                                    /app/ping-storm-topology/libs/
#COPY src-java/ping-topology/ping-storm-topology/build/dependency-jars/*                                         /app/ping-storm-topology/dependency-jars/
COPY src-java/ping-topology/ping-storm-topology/build.gradle                                                    /app/ping-storm-topology/



#portstate
COPY src-java/portstate-topology/portstate-storm-topology/build/libs/*                                          /app/portstate-storm-topology/libs/
#COPY src-java/portstate-topology/portstate-storm-topology/build/dependency-jars/*                               /app/portstate-storm-topology/dependency-jars/
COPY src-java/portstate-topology/portstate-storm-topology/build.gradle                                          /app/portstate-storm-topology/



#reroute
COPY src-java/reroute-topology/reroute-storm-topology/build/libs/*                                              /app/reroute-storm-topology/libs/
#COPY src-java/reroute-topology/reroute-storm-topology/build/dependency-jars/*                                   /app/reroute-storm-topology/dependency-jars/
COPY src-java/reroute-topology/reroute-storm-topology/build.gradle                                              /app/reroute-storm-topology/



#statsrouter
COPY src-java/statsrouter-topology/statsrouter-storm-topology/build/libs/*                                      /app/statsrouter-storm-topology/libs/
#COPY src-java/statsrouter-topology/statsrouter-storm-topology/build/dependency-jars/*                           /app/statsrouter-storm-topology/dependency-jars/
COPY src-java/statsrouter-topology/statsrouter-storm-topology/build.gradle                                      /app/statsrouter-storm-topology



#stats
COPY src-java/stats-topology/stats-storm-topology/build/libs/*                                                  /app/stats-storm-topology/libs/
#COPY src-java/stats-topology/stats-storm-topology/build/dependency-jars/*                                       /app/stats-storm-topology/dependency-jars/
COPY src-java/stats-topology/stats-storm-topology/build.gradle                                                  /app/stats-storm-topology/



#swmanager
COPY src-java/swmanager-topology/swmanager-storm-topology/build/libs/*                                          /app/swmanager-storm-topology/libs/
#COPY src-java/swmanager-topology/swmanager-storm-topology/build/dependency-jars/*                               /app/swmanager-storm-topology/dependency-jars/
COPY src-java/swmanager-topology/swmanager-storm-topology/build.gradle                                          /app/swmanager-storm-topology/

CMD /app/entry-point.sh

RUN TZ=Australia/Melbourne date >> /container_baked_on.txt
