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
ADD services/tools/ansible /ansible
ADD kilda-bins/apache/ /src
RUN ansible-playbook -v -s /ansible/deploy.yml
ADD services/tools/zookeeper-conf/zoo.cfg /opt/zookeeper/conf/zoo.cfg
ADD services/tools/zookeeper-conf/log4j.properties /opt/zookeeper/conf/log4j.properties
ADD services/tools/hbase-conf/hbase-env.sh /opt/hbase/conf/hbase-env.sh
ADD services/tools/hbase-conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml
ADD services/tools/hbase-conf/start-hbase /opt/hbase/bin/start-hbase
ADD services/tools/kafka-conf/server.properties /opt/kafka/config/server.properties
