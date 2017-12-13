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

import json
import logging
import pprint

import click
import time

from kilda.probe.entity.message import Message
from kilda.probe.messaging import receive_with_context, send_with_context

LOG = logging.getLogger(__name__)


@click.command(name='monitor')
@click.pass_obj
def monitor_command(ctx):
    def print_message(record):
        try:
            LOG.info('New message in topic:\n%s',
                     pprint.pformat(json.loads(record.value)))
        except Exception as ex:
            print(ex)
            print(record)

    receive_with_context(ctx, print_message)


@click.command(name='fake-bolt')
@click.pass_obj
@click.option('--count', default=3)
@click.option('--sleep', default=1)
def bolt_command(ctx, count, sleep):
    def print_message(record):
        try:
            data = json.loads(record.value)
            if data['type'] == 'CTRL_REQUEST':
                LOG.info('New message in topic:\n%s', pprint.pformat(data))
                for i in range(1, count + 1):
                    m = Message('CTRL_RESPONSE', 'probe',
                                {'topology': 'topology-X',
                                 'component': 'component-X',
                                 'task_id': 'bolt-{}'.format(i)},
                                data['correlation_id'])
                    send_with_context(ctx, m.serialize())
                    time.sleep(sleep)
        except Exception:
            LOG.exception('error')
            print(record)

    receive_with_context(ctx, print_message)
