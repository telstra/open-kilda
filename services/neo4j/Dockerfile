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
ADD ansible /ansible
RUN ansible-playbook -s /ansible/deploy.yml
ADD neo4j-config/neo4j.conf /etc/neo4j/neo4j.conf
ADD neo4j-config/auth /var/lib/neo4j/data/dbms/auth

#
# https://github.com/neo4j-contrib/neo4j-apoc-procedures#version-compatibility-matrix
#
# This means that care must be taken to match the version of apoc with the version of neo4j.
#
# Regarding use:
#   - Do this in neo4j browser page: CALL apoc.help("dijkstra")
#   - Reference: https://neo4j-contrib.github.io/neo4j-apoc-procedures/
#
RUN wget \
  https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/3.3.0.1/apoc-3.3.0.1-all.jar \
   -O /var/lib/neo4j/plugins/apoc-3.3.0.1-all.jar