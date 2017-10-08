FROM kilda/base-ubuntu
ADD kilda-bins/ /src
ADD services/storm/ansible /ansible
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/storm/conf/storm.yaml /opt/storm/conf/storm.yaml
