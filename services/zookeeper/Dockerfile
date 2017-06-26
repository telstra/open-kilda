FROM kilda/base-ubuntu
ADD services/zookeeper/ansible /ansible
ADD kilda-bins/apache/ /src
ADD services/zookeeper/ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/zookeeper/zookeeper-conf/zoo.cfg /opt/zookeeper/conf/zoo.cfg
ADD services/zookeeper/zookeeper-conf/log4j.properties /opt/zookeeper/conf/log4j.properties

EXPOSE 2181 2888 3888

WORKDIR /opt/zookeeper

