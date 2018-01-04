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
RUN apt-get update
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
RUN pip install gevent
RUN pip install kazoo
RUN pip install python-logstash
RUN chmod +x /usr/local/bin/uwsgi
ADD queue-engine/ /queue-engine
ADD supervisor/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
ADD entrypoint.sh /entrypoint.sh
