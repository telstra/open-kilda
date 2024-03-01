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

ADD BUILD/northbound/libs/northbound.jar /app/
WORKDIR /app

# This is an example of a command that starts Northbound with JMX enabled. You can connect to the specified IP and port
# with visualVM or other tool to get runtime information.
CMD ["java", "-Dcom.sun.management.jmxremote", "-Dcom.sun.management.jmxremote.rmi.port=9445", "-Dcom.sun.management.jmxremote.port=9445", "-Djava.rmi.server.hostname=172.19.114.89", "-Dcom.sun.management.jmxremote.local.only=false", "-Dcom.sun.management.jmxremote.ssl=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseContainerSupport", "-jar", "northbound.jar"]
EXPOSE 9445

#CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseContainerSupport", "-jar", "northbound.jar"]
