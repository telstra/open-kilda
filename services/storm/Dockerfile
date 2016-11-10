FROM kilda/base-ubuntu
ADD ansible /ansible
ADD ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD conf/storm.yaml /opt/storm/conf/storm.yaml
