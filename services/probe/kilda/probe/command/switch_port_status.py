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
import json
import requests
import traceback
import pprint

import click

from kilda.probe.entity.message import create_dump_state_by_switch

from kilda.probe.messaging import receive_with_context_async, send_with_context
from py2neo import Graph

from prettytable import PrettyTable

LOG = logging.getLogger(__name__)


def print_table(records):
    table = PrettyTable(
        ['PORT', 'FL-PORT', 'FL-LINK', 'NEO4J-SRC', 'NEO4J-DST',
         'NEO4J-REMOTE', 'WFM-ISL-FOUND', 'WFM-ISL-STATUS', 'RECEIVE-DROPPED',
         'RECEIVE-PACKETS', 'TRANSMIT-PACKETS'], align='l')
    for port_id, v in records.items():
        table.add_row(
            [port_id,
             v.get('FL_PORT', '-'),
             v.get('FL_LINK', '-'),
             v.get('NEO4J_SRC', '-'),
             v.get('NEO4J_DST', '-'),
             v.get('REMOTE', '-'),
             v.get('WFM_ISL_FOUND', '-'),
             v.get('WFM_ISL_STATUS', '-'),
             v.get('RECEIVE_DROPPED', '-'),
             v.get('RECEIVE_PACKETS', '-'),
             v.get('TRANSMIT_PACKETS', '-')
             ])
    print(table)


def fl_get_ports_stats_for_switch(ctx, switch_id, debug):
    url = "{}/wm/core/switch/{}/port/json".format(ctx.fl_host, switch_id)
    result = requests.get(url)
    result.raise_for_status()

    result_data = result.json()
    if debug:
        print(result)
        pprint.pprint(result_data)

    ports = result_data['port_reply'][0]['port']

    return {int(x['port_number']): {'RECEIVE_DROPPED': x['receive_dropped'],
                                    'RECEIVE_PACKETS': x['receive_packets'],
                                    'TRANSMIT_PACKETS': x['transmit_packets'],
                                    } for x in
            ports if x['port_number'].isdigit()}


def fl_get_ports_for_switch(ctx, switch_id, debug):
    url = "{}/wm/core/switch/{}/port-desc/json".format(ctx.fl_host, switch_id)
    result = requests.get(url)
    result.raise_for_status()

    if debug:
        print(result)
        print(result.json())

    result_data = result.json()

    if 'port_desc' in result_data:
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

    return []


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


def wfm_ports_for_switch(ctx, switch_id):
    message = create_dump_state_by_switch(ctx.correlation_id,
                                          'wfm/kilda.topo.disco-bolt',
                                          switch_id)

    with receive_with_context_async(ctx, 1) as records:
        send_with_context(ctx, message.serialize())

    if not records:
        LOG.error("wfm/kilda.topo.disco-bolt NO RESPONSE")

    retval = {}

    for record in records:
        data = json.loads(record.value)
        payload = data['payload']

        for port in payload['state']['discovery']:

            if port['consecutive_success'] == 0:
                status = 'DOWN'
            elif port['consecutive_failure'] == 0:
                status = 'UP'
            else:
                status = 'N/A'

            retval[int(port['port_id'])] = {
                'WFM_ISL_FOUND': 'FOUND' if port['found_isl'] else 'NOT FOUND',
                'WFM_ISL_STATUS': '{}'.format(status)
            }

    return retval


@click.command(name='switch-port-status')
@click.argument('switch-id')
@click.pass_obj
def switch_port_status_command(ctx, switch_id):
    try:
        fl_results = fl_get_ports_for_switch(ctx, switch_id, ctx.debug)
    except Exception as ex:
        traceback.print_exc()
        fl_results = {}

    try:
        fl_port_stats_results = fl_get_ports_stats_for_switch(ctx, switch_id,
                                                              ctx.debug)
    except Exception as ex:
        traceback.print_exc()
        fl_port_stats_results = {}

    try:
        wfm_results = wfm_ports_for_switch(ctx, switch_id)
        wfm_results_no_records = len(wfm_results) == 0
    except Exception as ex:
        traceback.print_exc()
        wfm_results = {}
        wfm_results_no_records = False

    try:
        neo4j_results = neo4g_ports_for_switch(ctx, switch_id)
    except Exception as ex:
        traceback.print_exc()
        neo4j_results = {}

    port_ids = set(list(fl_results.keys()) +
                   list(neo4j_results.keys()) +
                   list(wfm_results.keys()) +
                   list(fl_port_stats_results.keys())
                   )

    results = {}

    if wfm_results_no_records:
        wfm_results = {port_id: {'WFM_ISL_FOUND': 'NO DATA'} for port_id in
                       port_ids}

    for port in port_ids:
        results[port] = {}
        results[port].update(fl_results.get(port, {}))
        results[port].update(neo4j_results.get(port, {}))
        results[port].update(wfm_results.get(port, {}))
        results[port].update(fl_port_stats_results.get(port, {}))

    print_table(results)
