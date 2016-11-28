FROM kilda/base-ubuntu
ADD services/floodlight/ansible /ansible
ADD services/floodlight/app /app
RUN ansible-playbook -s /ansible/deploy.yml
