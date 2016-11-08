FROM kilda/base-ubuntu
ADD ansible /ansible
ADD ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD kafka-conf/server.properties /opt/kafka/config/server.properties
