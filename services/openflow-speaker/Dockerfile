FROM kilda/base-ubuntu
ADD services/openflow-speaker/ansible /ansible
ADD services/openflow-speaker/app /app
RUN ansible-playbook -s /ansible/deploy.yml