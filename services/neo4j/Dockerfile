FROM kilda/base-ubuntu
ADD services/neo4j/ansible /ansible
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/neo4j/neo4j-config/neo4j.conf /etc/neo4j/neo4j.conf
ADD services/neo4j/neo4j-config/auth /var/lib/neo4j/data/dbms/auth