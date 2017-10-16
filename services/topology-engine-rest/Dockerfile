# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

FROM kilda/base-ubuntu

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update
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

