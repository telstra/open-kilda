FROM kilda/base-ubuntu
RUN apt-get install -y python-pip
RUN apt-get install -y supervisor
RUN apt-get install -y nginx
RUN apt-get install -y vim
RUN pip install --upgrade pip
RUN pip install neo4j-driver
RUN pip install kafka
RUN pip install uwsgi
RUN pip install pyotp
RUN pip install jsonschema
RUN pip install py2neo
RUN pip install requests
RUN chmod +x /usr/local/bin/uwsgi
ADD queue-engine/ /queue-engine
ADD supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD entrypoint.sh /entrypoint.sh

