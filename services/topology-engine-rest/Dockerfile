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
RUN pip install py2neo
RUN pip install requests
RUN chmod +x /usr/local/bin/uwsgi
ADD app/ /app
ADD supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD nginx/sites/ /etc/nginx/sites-available/
ADD db/database.db /var/data/
ADD entrypoint.sh /entrypoint.sh

