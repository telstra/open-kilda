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

from kilda.probe.entity.message import create_dump_state
from kilda.probe.messaging import send_with_context
from kilda.probe.messaging import receive_with_context_async

LOG = logging.getLogger(__name__)


def print_flow(flow, border):
    table = PrettyTable(['Property', 'Forward', 'Reverse'], border=border,
                        valign='m')
    for k, v in flow['forward'].items():
        if k == 'flowpath':

            table.add_row(['flowpath:latency_ns', v['latency_ns'],
                           flow['reverse'][k]['latency_ns']])
        else:
            table.add_row([k, v, flow['reverse'][k]])

    table.add_row(['path', print_path(flow['forward'], border),
                   print_path(flow['reverse'], border)])

    print(table)


def print_path(flow, border):
    path = flow['flowpath']['path']
    keys = ['switch_id', 'port_no', 'segment_latency', 'seq_id']
    table = PrettyTable(keys, border=border, vrules=prettytable.NONE,
                        hrules=prettytable.HEADER,
                        padding_width=0)

    for p in path:
        table.add_row([p.get(x, None) for x in keys])

    return table


def print_isls_tower(isls, border):
    table = PrettyTable(['Isl'], border=border)
    for isl in isls:
        child_table = PrettyTable(['Property', 'Value'], border=border)
        for k, v in isl.items():
            if k == 'path':
                for p in v:
                    for kk, vv in p.items():
                        child_table.add_row(
                            ['path:{}:{}'.format(p['seq_id'], kk), vv])
            else:
                child_table.add_row([k, v])
        table.add_row([child_table])
    print(table)


def print_isls(isls, border):
    if not isls:
        return
    columns = set()
    raw = []
    for isl in isls:
        d = isl.copy()
        if 'path' in d:
            for p in d['path']:
                for kk, vv in p.items():
                    d['p{}:{}'.format(p['seq_id'], kk)] = vv
        raw.append(d)
        columns.update(d.keys())

    columns -= {'id', 'path', 'message_type', 'p0:segment_latency',
                'created_in_cache', 'updated_in_cache', 'clazz'}

    sorted_columns = ['id'] + sorted(list(columns)) + ['created_in_cache',
                                                       'updated_in_cache']

    sorted_columns_with_names = OrderedDict(
        zip(sorted_columns, sorted_columns))

    sorted_columns_with_names.update({'available_bandwidth': 'av/bw',
                                      'created_in_cache': 'created',
                                      'updated_in_cache': 'updated',
                                      'latency_ns': 'lat'})

    table = PrettyTable(sorted_columns_with_names.values(),
                        border=border,
                        sortby='id',
                        vrules=prettytable.FRAME,
                        hrules=prettytable.FRAME)

    convert_timefied_to_human(raw)

    for d in raw:
        table.add_row([d.get(x, '-') for x in sorted_columns_with_names.keys()])

    print(table)


def convert_timefied_to_human(data):
    for r in data:
        for time_field in ['created_in_cache', 'updated_in_cache']:
            if time_field in r:
                r[time_field] = datetime.utcfromtimestamp(r[time_field])


def print_switches(switches, border):
    if not switches:
        return

    columns = set(switches[0].keys())
    columns -= {'switch_id', 'created_in_cache', 'updated_in_cache'}

    sorted_columns = ['switch_id'] + sorted(columns) + ['created_in_cache',
                                                        'updated_in_cache']

    sorted_columns_with_names = OrderedDict(
        zip(sorted_columns, sorted_columns))

    sorted_columns_with_names.update({'created_in_cache': 'created',
                                      'updated_in_cache': 'updated'})

    table = PrettyTable(sorted_columns_with_names.values(),
                        border=border,
                        sortby='switch_id',
                        vrules=prettytable.FRAME,
                        hrules=prettytable.FRAME)

    convert_timefied_to_human(switches)

    for s in switches:
        table.add_row([s[x] for x in sorted_columns_with_names.keys()])

    print(table)


def print_flows_from_payload(payload, border):
    flows = payload['state']['flow']['flows']
    if flows:
        print('+----------')
        print('|  Flows')
        for flow in flows:
            print_flow(flow, border)


def cache_bolt_print_table(payload, border):
    print_flows_from_payload(payload, border)

    isls = payload['state']['network']['isls']
    if isls:
        print('+----------')
        print('|  Isls')
        print_isls(isls, border)

    switches = payload['state']['network']['switches']
    if switches:
        print('+----------')
        print('|  Switches')
        print_switches(switches, border)


def crud_bolt_print_table(payload, border):
    print_flows_from_payload(payload, border)


def print_table(records, border):
    for record in records:
        data = json.loads(record.value)
        payload = data['payload']
        LOG.debug(pprint.pformat(data))
        table = PrettyTable(['Topology', 'Component', 'Task ID'],
                            border=border)
        table.add_row(
            [payload['topology'], payload['component'], payload['task_id']])

        print(table)

        clazz = payload['state']['clazz']
        if clazz == 'org.openkilda.messaging.ctrl.state.CacheBoltState':
            cache_bolt_print_table(payload, border)
        elif clazz == 'org.openkilda.messaging.ctrl.state.CrudBoltState':
            crud_bolt_print_table(payload, border)
        else:
            print(pprint.pformat(payload['state']))

        print('\n')


@click.command(name='dump-state')
@click.argument('destination')
@click.option('--border/--no-border', default=True)
@click.option('--table', 'output_type', flag_value='table', default=True)
@click.option('--json', 'output_type', flag_value='json')
@click.option('--allow-dangerous-operation/--prevent-dangerous-operation', default=False)
@click.pass_obj
def dump_state_command(ctx, destination, border, output_type, allow_dangerous_operation):

    if not allow_dangerous_operation:
        click.secho("DON'T RUN ON PRODUCTION MAY CAUSE OVERSIZED KAFKA MESSAGE",
                    blink=True, bold=True)
        return

    message = create_dump_state(ctx.correlation_id, destination=destination)
    LOG.debug('command = {}'.format(message.serialize()))

    with receive_with_context_async(ctx) as records:
        send_with_context(ctx, message.serialize())

    if output_type == 'table':
        print_table(records, border)
    elif output_type == 'json':
        for record in records:
            data = json.loads(record.value)
            print(pprint.pformat(data))
