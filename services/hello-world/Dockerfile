FROM kilda/base-ubuntu
ADD services/hello-world/ansible /ansible
ADD services/hello-world/app /app
RUN ansible-playbook -s /ansible/deploy.yml