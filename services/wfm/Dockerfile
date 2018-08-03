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

FROM kilda/storm:latest

ENV DEBIAN_FRONTEND noninteractive

RUN pip install kafka
RUN echo "PATH=$PATH:/opt/storm/bin" >> ~/.bashrc

WORKDIR /app
ADD app/deploy_topos_and_monitor.sh /app/
RUN chmod 777 /app/deploy_topos_and_monitor.sh
ADD Makefile /app/
ADD src/main/resources/topology.properties /app/
CMD ["/app/deploy_topos_and_monitor.sh", "topology.properties"]

ADD target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar /app/target/

RUN TZ=Australia/Melbourne date >> /container_baked_on.txt
