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

import logging
import time
import socket

import click

from kilda.probe.command.flow_validation import validate_flows
from kilda.probe.command.dump_resource_state import dump_resource_state
from kilda.probe.command.list import list_command
from kilda.probe.command.monitor import monitor_command, bolt_command
from kilda.probe.command.dump_state import dump_state_command
from kilda.probe.command.switch_port_status import switch_port_status_command

LOG = logging.getLogger(__name__)


def init_logger(level):
    if level <= logging.DEBUG:
        logging.basicConfig(level=level,
                            format='%(asctime)s - %(name)s - %(levelname)s | '
                                   '%(message)s')
    else:
        logging.basicConfig(level=level,
                            format='%(asctime)s  | %(message)s')

        logging.getLogger('kafka').setLevel(logging.ERROR)
        logging.getLogger('neo4j').setLevel(logging.ERROR)
        logging.getLogger('httpstream').setLevel(logging.ERROR)


def generate_correlation_id():
    return 'probe-{}-{}'.format(socket.gethostname(),
                                int(round(time.time() * 1000)))


class Context(object):
    def __init__(self):
        self._debug = False
        self._correlation_id = None
        self._kafka_bootstrap_servers = None
        self._kafka_topic = None
        self._timeout = None
        self._fl_host = None
        self._neo4j_host = None
        self._neo4j_user = None
        self._neo4j_pass = None
        self._nb_endpoint = None
        self._nb_user = None
        self._nb_pass = None

    @property
    def debug(self):
        return self._debug

    @debug.setter
    def debug(self, value):
        self._debug = value

    @property
    def correlation_id(self):
        return self._correlation_id

    @correlation_id.setter
    def correlation_id(self, value):
        self._correlation_id = value

    @property
    def kafka_bootstrap_servers(self):
        return self._kafka_bootstrap_servers

    @kafka_bootstrap_servers.setter
    def kafka_bootstrap_servers(self, value):
        self._kafka_bootstrap_servers = value

    @property
    def kafka_topic(self):
        return self._kafka_topic

    @kafka_topic.setter
    def kafka_topic(self, value):
        self._kafka_topic = value

    @property
    def fl_host(self):
        return self._fl_host

    @fl_host.setter
    def fl_host(self, value):
        self._fl_host = value

    @property
    def neo4j_host(self):
        return self._neo4j_host

    @neo4j_host.setter
    def neo4j_host(self, value):
        self._neo4j_host = value

    @property
    def neo4j_user(self):
        return self._neo4j_user

    @neo4j_user.setter
    def neo4j_user(self, value):
        self._neo4j_user = value

    @property
    def neo4j_pass(self):
        return self._neo4j_pass

    @neo4j_pass.setter
    def neo4j_pass(self, value):
        self._neo4j_pass = value

    @property
    def timeout(self):
        return self._timeout

    @timeout.setter
    def timeout(self, value):
        self._timeout = value

    @property
    def nb_endpoint(self):
        return self._nb_endpoint

    @nb_endpoint.setter
    def nb_endpoint(self, value):
        self._nb_endpoint = value

    @property
    def nb_user(self):
        return self._nb_user

    @nb_user.setter
    def nb_user(self, value):
        self._nb_user = value

    @property
    def nb_pass(self):
        return self._nb_pass

    @nb_pass.setter
    def nb_pass(self, value):
        self._nb_pass = value


@click.group()
@click.option('--debug/--no-debug', default=False, envvar='DEBUG')
@click.option('--correlation-id', default=generate_correlation_id())
@click.option('--kafka-bootstrap-servers', default='localhost',
              envvar='KAFKA_BOOTSTRAP_SERVERS')
@click.option('--kafka-topic', default='kilda.ctrl', envvar='KAFKA_TOPIC')
@click.option('--fl-host', default='http://localhost:8180', envvar='FL')
@click.option('--neo4j-host', default='localhost',
              envvar='NEO4G_HOST')
@click.option('--neo4j-user', default='neo4j', envvar='NEO4G_USER')
@click.option('--neo4j-pass', default='temppass', envvar='NEO4G_PASS')
@click.option('--timeout', default=2)
@click.option('--nb-endpoint', default='http://localhost:8080',
              envvar='NB_ENDPOINT')
@click.option('--nb-user', default='kilda', envvar='NB_USER')
@click.option('--nb-pass', default='kilda', envvar='NB_PASS')
@click.pass_obj
def cli(ctx, debug, correlation_id, kafka_bootstrap_servers, kafka_topic,
        fl_host, neo4j_host, neo4j_user, neo4j_pass, timeout, nb_endpoint,
        nb_user, nb_pass):
    init_logger(logging.DEBUG if debug else logging.INFO)
    ctx.debug = debug
    ctx.correlation_id = correlation_id
    LOG.debug('correlation_id = %s', correlation_id)
    ctx.kafka_bootstrap_servers = kafka_bootstrap_servers
    ctx.kafka_topic = kafka_topic
    ctx.timeout = timeout
    ctx.fl_host = fl_host
    ctx.neo4j_host = neo4j_host
    ctx.neo4j_user = neo4j_user
    ctx.neo4j_pass = neo4j_pass
    ctx.nb_endpoint = nb_endpoint
    ctx.nb_user = nb_user
    ctx.nb_pass = nb_pass


cli.add_command(list_command)
cli.add_command(monitor_command)
cli.add_command(bolt_command)
cli.add_command(dump_state_command)
cli.add_command(switch_port_status_command)
cli.add_command(dump_resource_state)
cli.add_command(validate_flows)


def main():
    cli(obj=Context())
