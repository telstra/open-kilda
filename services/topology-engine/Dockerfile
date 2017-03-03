FROM kilda/base-ubuntu
RUN apt-get install -y python-pip
RUN apt-get install -y supervisor
RUN apt-get install -y nginx
RUN apt-get install -y vim
RUN apt-get install -y sqlite
RUN pip install --upgrade pip
RUN pip install neo4j-driver
RUN pip install kafka
RUN pip install flask
RUN pip install flask-login
RUN pip install flask-sqlalchemy
RUN pip install uwsgi
RUN pip install pyotp
RUN pip install jsonschema
RUN chmod +x /usr/local/bin/uwsgi
ADD services/topology-engine/app /app
ADD services/topology-engine/queue-engine/ /queue-engine
ADD services/topology-engine/supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD services/topology-engine/nginx/sites/ /etc/nginx/sites-available/
ADD services/topology-engine/entrypoint.sh /entrypoint.sh
