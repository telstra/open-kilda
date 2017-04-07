FROM kilda/base-ubuntu
ADD kilda-bins/apache/ /src
ADD services/kafka/ansible /ansible
ADD services/kafka/ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/kafka/kafka-conf/server.properties /opt/kafka/config/server.properties
ADD services/kafka/kafka-conf/create_topics.sh /opt/kafka/bin/create_topics.sh
ADD services/kafka/kafka-conf/run_and_configure.sh /opt/kafka/bin/
