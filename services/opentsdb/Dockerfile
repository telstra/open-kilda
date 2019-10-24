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

ARG base_image=kilda/hbase
FROM ${base_image}

WORKDIR /tmp/

COPY app /app

RUN    wget -q https://github.com/OpenTSDB/opentsdb/releases/download/v2.3.0/opentsdb-2.3.0_all.deb \
    && apt-get update -q \
    && apt-get install -yq --no-install-recommends \
        gnuplot \
        python-happybase \
    && dpkg -i opentsdb-2.3.0_all.deb \
    && chmod 777 /app/* \
    && rm -rfv /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY conf/opentsdb.conf /etc/opentsdb/opentsdb.conf
