FROM kilda/hbase
ADD ansible /ansible
ADD ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -v -s /ansible/deploy.yml
ADD conf/opentsdb.conf /etc/opentsdb/opentsdb.conf
ADD app /app
RUN chmod 777 /app/*
