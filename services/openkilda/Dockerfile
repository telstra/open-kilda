FROM kilda/floodlight
ADD ansible /ansible
ADD app /app
ADD config /app/config
RUN ansible-playbook -s /ansible/deploy.yml
