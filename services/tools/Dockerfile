FROM kilda/base-ubuntu
ADD services/tools/ansible /ansible
ADD kilda-bins/apache/ /src
ADD services/tools/ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -v -s /ansible/deploy.yml
ADD services/tools/zookeeper-conf/zoo.cfg /opt/zookeeper/conf/zoo.cfg
ADD services/tools/zookeeper-conf/log4j.properties /opt/zookeeper/conf/log4j.properties
ADD services/tools/hbase-conf/hbase-env.sh /opt/hbase/conf/hbase-env.sh
ADD services/tools/hbase-conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml
ADD services/tools/hbase-conf/start-hbase /opt/hbase/bin/start-hbase
ADD services/tools/kafka-conf/server.properties /opt/kafka/config/server.properties
