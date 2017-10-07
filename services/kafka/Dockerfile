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
ADD kilda-bins/apache/ /src
ADD services/kafka/ansible /ansible
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/kafka/kafka-conf/server.properties /opt/kafka/config/server.properties
ADD services/kafka/kafka-conf/create_topics.sh /opt/kafka/bin/create_topics.sh
ADD services/kafka/kafka-conf/run_and_configure.sh /opt/kafka/bin/
