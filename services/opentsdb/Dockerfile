FROM kilda/hbase
ADD services/opentsdb/ansible /ansible
RUN ansible-playbook -v -s /ansible/deploy.yml
ADD services/opentsdb/conf/opentsdb.conf /etc/opentsdb/opentsdb.conf
ADD services/opentsdb/app /app
RUN chmod 777 /app/*
