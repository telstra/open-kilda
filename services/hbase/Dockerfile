FROM kilda/base-ubuntu
ADD ansible /ansible
ADD ubuntu/sources.list /etc/apt/sources.list
RUN ansible-playbook -s /ansible/deploy.yml
ADD hbase-conf/hbase-env.sh /opt/hbase/conf/hbase-env.sh
ADD hbase-conf/hbase-site.xml /opt/hbase/conf/hbase-site.xml
ADD hbase-conf/start-hbase /opt/hbase/bin/start-hbase
