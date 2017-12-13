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

from kilda.probe.command.list import list_command
from kilda.probe.command.monitor import monitor_command, bolt_command
from kilda.probe.command.dump_state import dump_state_command

LOG = logging.getLogger(__name__)


def init_logger(level):
    if level <= logging.DEBUG:
        logging.basicConfig(level=level,
                            format='%(asctime)s - %(name)s - %(levelname)s | '
                                   '%(message)s')
    else:
        logging.basicConfig(level=level,
                            format='%(asctime)s | %(message)s')

        kafka = logging.getLogger('kafka')
        kafka.setLevel(logging.ERROR)


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
    def timeout(self):
        return self._timeout

    @timeout.setter
    def timeout(self, value):
        self._timeout = value


@click.group()
@click.option('--debug/--no-debug', default=False, envvar='DEBUG')
@click.option('--correlation-id', default=generate_correlation_id())
@click.option('--kafka-bootstrap-servers', default='localhost',
              envvar='KAFKA_BOOTSTRAP_SERVERS')
@click.option('--kafka-topic', default='kilda.ctrl', envvar='KAFKA_TOPIC')
@click.option('--timeout', default=2)
@click.pass_obj
def cli(ctx, debug, correlation_id, kafka_bootstrap_servers, kafka_topic,
        timeout):
    init_logger(logging.DEBUG if debug else logging.INFO)
    ctx.debug = debug
    ctx.correlation_id = correlation_id
    LOG.debug('correlation_id = %s', correlation_id)
    ctx.kafka_bootstrap_servers = kafka_bootstrap_servers
    ctx.kafka_topic = kafka_topic
    ctx.timeout = timeout


cli.add_command(list_command)
cli.add_command(monitor_command)
cli.add_command(bolt_command)
cli.add_command(dump_state_command)


def main():
    cli(obj=Context())
