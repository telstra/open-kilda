FROM kilda/base-ubuntu
ADD services/topology-engine/ansible /ansible
ADD services/topology-engine/app /app
RUN ansible-playbook -s /ansible/deploy.yml