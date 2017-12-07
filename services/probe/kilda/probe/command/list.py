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
import pprint
import json

import click

from kilda.probe.entity.message import create_list
from kilda.probe.messaging import send_with_context, \
    receive_with_context_async
from prettytable import PrettyTable

LOG = logging.getLogger(__name__)


@click.command(name='list')
@click.pass_obj
def list_command(ctx):
    message = create_list(ctx.correlation_id)
    LOG.debug('command = {}'.format(message.serialize()))

    with receive_with_context_async(ctx) as records:
        send_with_context(ctx, message.serialize())

    table = PrettyTable(['Topology', 'Component', 'Task ID'])
    for record in records:
        data = json.loads(record.value)
        payload = data['payload']
        LOG.debug(pprint.pformat(data))
        table.add_row(
            [payload['topology'], payload['component'], payload['task_id']])
    print(table)
