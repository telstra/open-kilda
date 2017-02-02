FROM kilda/base-ubuntu
ADD ansible /ansible
ADD app /app
RUN ansible-playbook -s /ansible/deploy.yml
