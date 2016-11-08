FROM kilda/base-ubuntu
ADD ansible /ansible
ADD ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD zookeeper-conf/zoo.cfg /opt/zookeeper/conf/zoo.cfg
ADD zookeeper-conf/log4j.properties /opt/zookeeper/conf/log4j.properties
