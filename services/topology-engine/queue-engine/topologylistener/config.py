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
import os


_root = os.path.dirname(__file__)

config = ConfigParser.RawConfigParser()
path = os.path.join(_root, os.pardir, 'topology_engine.ini')
config.read(path)

_dummy = object()


def read_option(section, name, conv=None, default=_dummy):
    try:
        value = config.get(section, name)
    except ConfigParser.NoOptionError:
        if default is _dummy:
            raise ValueError('Option [{}]{} - not found'.format(section, name))
        value = default

    if conv is not None:
        value = conv(value)

    return value


def get(section, option):
    return config.get(section, option)


def getint(section, option):
    return config.getint(section, option)


def getboolean(section, option):
    return config.getboolean(section, option)


def _get_bootstrap_servers():
    bootstrap_servers_property = config.get('kafka', 'bootstrap.servers')
    return [x.strip() for x in bootstrap_servers_property.split(',')]

try:
    ENVIRONMENT_NAMING_PREFIX = config.get('kafka',
                                           'environment.naming.prefix')
except ConfigParser.NoOptionError:
    ENVIRONMENT_NAMING_PREFIX = None


def read_and_format_with_env_name(name):
    topic_config_name = read_option('kafka', name)
    if ENVIRONMENT_NAMING_PREFIX is None:
        return topic_config_name
    return '_'.join([ENVIRONMENT_NAMING_PREFIX, topic_config_name])


KAFKA_BOOTSTRAP_SERVERS = _get_bootstrap_servers()
KAFKA_FLOW_TOPIC = read_and_format_with_env_name('flow.topic')
KAFKA_CACHE_TOPIC = read_and_format_with_env_name('cache.topic')
KAFKA_SPEAKER_TOPIC = read_and_format_with_env_name('speaker.topic')
KAFKA_TOPO_ENG_TOPIC = read_and_format_with_env_name('topo.eng.topic')
KAFKA_NORTHBOUND_TOPIC = read_and_format_with_env_name('northbound.topic')
KAFKA_CONSUMER_GROUP = read_and_format_with_env_name('consumer.group')

ZOOKEEPER_HOSTS = config.get('zookeeper', 'hosts')

NEO4J_SOCKET_TIMEOUT = read_option(
        'neo4j', 'socket.timeout', conv=float, default=30.0)

ISL_COST_WHEN_PORT_DOWN = read_option(
        'isl', 'cost_when_port_down', conv=int, default=10000)
