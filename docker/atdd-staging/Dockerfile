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

ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ADD target/atdd-staging-*.jar /app/atdd-staging.jar
ADD target/lib/*.jar /app/lib/
ADD log4j2.xml /app/
ADD kilda.properties /app/

WORKDIR /app

CMD ["java", "-cp", "/app/atdd-staging.jar:/app/lib/*", \
             "-Dlog4j.configurationFile=/app/log4j2.xml", \
             "-Dkilda.config.file=/app/kilda.properties", \
             "-Dtopology.definition.file=/app/topology.yaml", \
             "cucumber.api.cli.Main"]
