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
import requests

import click
from py2neo import Graph

from prettytable import PrettyTable

LOG = logging.getLogger(__name__)


def print_table(records):
    table = PrettyTable(
        ['PORT', 'FL-PORT', 'FL-LINK', 'NEO4J-SRC', 'NEO4J-DST', 'REMOTE'])
    for port_id, v in records.items():
        table.add_row(
            [port_id,
             v.get('FL_PORT', '-'),
             v.get('FL_LINK', '-'),
             v.get('NEO4J_SRC', '-'),
             v.get('NEO4J_DST', '-'),
             v.get('REMOTE', '-')
             ])
    print(table)


def fl_get_ports_for_switch(ctx, switch_id):
    url = "{}/wm/core/switch/{}/port-desc/json".format(ctx.fl_host, switch_id)
    result = requests.get(url)
    result.raise_for_status()

    def get_status(sw):

        r = {'FL_PORT': 'PORT_UP',
             'FL_LINK': 'LINK_UP'}

        if 'PORT_DOWN' in sw['config']:
            r['FL_PORT'] = 'PORT_DOWN'
        if 'LINK_DOWN' in sw['state']:
            r['FL_LINK'] = 'LINK_DOWN'

        return r

    return {int(x['port_number']): get_status(x) for x in
            result.json()['port_desc'] if x['port_number'].isdigit()}


def neo4g_ports_for_switch(ctx, switch_id):
    query = 'MATCH ()-[r:isl]->() WHERE r.src_switch = "{}" OR r.dst_switch ' \
            '= "{}" RETURN r'.format(switch_id, switch_id)

    graph = Graph("http://{}:{}@{}:7474/db/data/".format(
        ctx.neo4j_user,
        ctx.neo4j_pass,
        ctx.neo4j_host))

    result = graph.run(query).data()

    def get_status(link):
        if link['src_switch'] == switch_id:
            return {'NEO4J_SRC': link['status']}
        return {'NEO4J_DST': link['status']}

    def get_port_from_link(link):
        if link['src_switch'] == switch_id:
            return link['src_port']
        return link['dst_port']

    retval = {}

    for x in result:
        x = x['r']
        port_id = int(get_port_from_link(x))

        if port_id not in retval:
            retval[port_id] = {}

        retval[port_id].update(get_status(x))

        if x['src_switch'] == switch_id:
            retval[port_id]['REMOTE'] = '{}-{}'.format(x['dst_switch'],
                                                       x['dst_port'])
    return retval


@click.command(name='switch-port-status')
@click.argument('switch-id')
@click.pass_obj
def switch_port_status_command(ctx, switch_id):
    fl_results = fl_get_ports_for_switch(ctx, switch_id)
    results = {}

    neo4j_results = neo4g_ports_for_switch(ctx, switch_id)

    port_ids = set(list(fl_results.keys()) + list(neo4j_results.keys()))

    for port in port_ids:
        results[port] = {}
        results[port].update(fl_results.get(port, {}))
        results[port].update(neo4j_results.get(port, {}))

    print_table(results)
