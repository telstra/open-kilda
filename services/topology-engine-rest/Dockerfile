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

RUN apt-get update && apt-get install -y \
    python-pip \
    supervisor \
    nginx \
    vim \
    sqlite
RUN pip install --upgrade pip
RUN pip install \
    neo4j-driver \
    kafka \
    flask \
    flask-login \
    flask-sqlalchemy \
    uwsgi \
    pyotp \
    jsonschema \
    py2neo \
    requests \
    python-logstash
ADD nginx/sites/ /etc/nginx/sites-available/
ADD supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD db/database.db /var/data/
ADD app/ /app
RUN mkdir -p /app/logs/uwsgi

CMD /usr/bin/supervisord
