FROM kilda/base-ubuntu
ADD kilda-bins/apache/ /src
ADD services/storm/ansible /ansible
ADD services/storm/ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/storm/conf/storm.yaml /opt/storm/conf/storm.yaml
