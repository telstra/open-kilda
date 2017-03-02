FROM kilda/base-ubuntu
RUN apt-get install -y python-pip
RUN apt-get install -y supervisor
RUN apt-get install -y nginx
RUN apt-get install -y vim
RUN pip install neo4j-driver
RUN pip install kafka
RUN pip install flask
RUN pip install jsonschema
ADD app/ /app/
ADD supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD nginx/sites/ /etc/nginx/sites-available/
ADD entrypoint.sh /entrypoint.sh
RUN chown www-data:www-data /app/html -R
