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

RUN    wget -O - https://debian.neo4j.org/neotechnology.gpg.key | apt-key add - \
    && echo 'deb https://debian.neo4j.org/repo stable/' | tee /etc/apt/sources.list.d/neo4j.list \
    && apt-get update -q \
    && apt-get install -yq --no-install-recommends \
        neo4j=1:3.3.3 \
    && apt-get autoremove -yq \
    && pip install \
        cycli \
    && wget -q \
        https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/3.3.0.1/apoc-3.3.0.1-all.jar \
        -O /var/lib/neo4j/plugins/apoc-3.3.0.1-all.jar \
    && wget -q \
        https://github.com/neo4j-contrib/neo4j-graph-algorithms/releases/download/3.3.2.0/graph-algorithms-algo-3.3.2.0.jar \
        -O /var/lib/neo4j/plugins/graph-algorithms-algo-3.3.2.0.jar \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /var/lib/neo4j

COPY neo4j-config/neo4j.conf /etc/neo4j/neo4j.conf
COPY init/neo4j-queries.cql /app/neo4j-queries.cql
COPY init/init.sh /app/init.sh

CMD /app/init.sh
