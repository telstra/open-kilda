FROM kilda/base-ubuntu
ADD services/hbase/ansible /ansible
ADD kilda-bins/apache/ /src
RUN ansible-playbook -s /ansible/deploy.yml
ADD services/hbase/hbase-conf/hbase-env.sh /opt/hbase/conf/hbase-env.sh
ADD services/hbase/hbase-conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml
ADD services/hbase/hbase-conf/start-hbase /opt/hbase/bin/start-hbase
