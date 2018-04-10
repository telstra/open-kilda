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

import ConfigParser

config = ConfigParser.RawConfigParser()
config.read('topology_engine.properties')


def get(section, option):
    return config.get(section, option)


def getint(section, option):
    return config.getint(section, option)


def getboolean(section, option):
    return config.getboolean(section, option)


def _get_bootstrap_servers():
    bootstrap_servers_property = config.get('kafka', 'bootstrap.servers')
    return [x.strip() for x in bootstrap_servers_property.split(',')]


KAFKA_BOOTSTRAP_SERVERS = _get_bootstrap_servers()
KAFKA_FLOW_TOPIC = config.get('kafka', 'flow.topic')
KAFKA_CACHE_TOPIC = config.get('kafka', 'cache.topic')
KAFKA_SPEAKER_TOPIC = config.get('kafka', 'speaker.topic')
KAFKA_TOPO_ENG_TOPIC = config.get('kafka', 'topo.eng.topic')
KAFKA_NORTHBOUND_TOPIC = config.get('kafka', 'northbound.topic')

ZOOKEEPER_HOSTS = config.get('zookeeper', 'hosts')

try:
    value = config.getfloat('neo4j', 'socket.timeout')
except ConfigParser.NoOptionError:
    value = 30.0

NEO4J_SOCKET_TIMEOUT = value

del value
