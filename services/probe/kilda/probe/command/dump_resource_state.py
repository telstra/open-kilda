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
from collections import OrderedDict

import click
from datetime import datetime
import prettytable.prettytable as prettytable
from prettytable import PrettyTable

import pprint

from kilda.probe.entity.message import create_dump_state, \
    create_resource_dump_state
from kilda.probe.messaging import send_with_context
from kilda.probe.messaging import receive_with_context_async

LOG = logging.getLogger(__name__)


def chunks(l, n):
    """Yield successive n-sized chunks from l."""
    for i in range(0, len(l), n):
        yield l[i:i + n]


def print_table(records, border):
    for record in records:
        data = json.loads(record.value)
        payload = data['payload']
        LOG.debug(pprint.pformat(data))
        table = PrettyTable(['Topology', 'Component', 'Task ID'],
                            border=border)
        table.add_row(
            [payload['topology'], payload['component'], payload['task_id']])

        del payload['state']['clazz']

        print(table)

        table = PrettyTable(['Resource', 'List', 'Count'], border=border)

        for k in ('cookies', 'vlans'):

            #payload['state'][k] = list(range(1, 4096))

            data = payload['state'][k]

            rep = format_int_list(data)

            table.add_row([k, rep, len(payload['state'][k])])

            print(table)

        table = PrettyTable(['SW', 'List', 'Count'])

        for sw in payload['state']['meters']:
            data = payload['state']['meters'][sw]
            rep = format_int_list(data)
            table.add_row([sw, rep, len(data)])

        print(table)

        print('\n')


def format_int_list(data):
    data = list(map(str, data))
    rep = ""
    for x in chunks(data, 10):
        rep += ", ".join(x)
        rep += "\n"
    return rep


@click.command(name='dump-resource-state')
@click.argument('destination')
@click.option('--border/--no-border', default=True)
@click.option('--table', 'output_type', flag_value='table', default=True)
@click.option('--json', 'output_type', flag_value='json')
@click.pass_obj
def dump_resource_state(ctx, destination, border, output_type):
    message = create_resource_dump_state(ctx.correlation_id,
                                         destination=destination)
    LOG.debug('command = {}'.format(message.serialize()))

    with receive_with_context_async(ctx) as records:
        send_with_context(ctx, message.serialize())

    if output_type == 'table':
        print_table(records, border)
    elif output_type == 'json':
        for record in records:
            data = json.loads(record.value)
            pprint.pprint(data)
