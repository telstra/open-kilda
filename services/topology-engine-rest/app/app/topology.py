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
import datetime
import logging
import json

from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response, jsonify
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user
import py2neo
import pytz
from werkzeug import exceptions as http_errors

from app import application
from . import neo4j_tools

logger = logging.getLogger(__name__)

config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

neo4j_connect = neo4j_tools.connect(config)

DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S.%fZ'
UNIX_EPOCH = datetime.datetime(1970, 1, 1, 0, 0, 0, 0, pytz.utc)


@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    query = 'MATCH (n:switch) return n'
    topology = []
    for record in neo4j_connect.run(query).data():
        record = record['n']
        relations = [
            rel['dst_switch']
            for rel in neo4j_connect.match(nodes=[record], r_type='isl')]
        relations.sort()
        topology.append({
            'name': record['name'],
            'outgoing_relationships': relations
        })

    topology.sort(key=lambda x:x['name'])
    topology = {'nodes': topology}

    return json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True)


class Nodes(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Edge(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Link(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


@application.route('/api/v1/topology/nodes')
@login_required
def api_v1_topology_nodes():
    edges = []
    for record in neo4j_connect.run('MATCH (n:switch) return n').data():
        source = record['n']

        for rel in neo4j_connect.match(nodes=[source], r_type='isl'):
            dest = rel.end_node()

            s = Link()
            s.label = source['name']
            s.id = py2neo.remote(source)._id

            t = Link()
            t.label = dest['name']
            t.id = py2neo.remote(dest)._id

            edge = Edge()
            edge.value = "{} to {}".format(s.label, t.label)
            edge.source = s
            edge.target = t

            edges.append(edge)

    edges.sort(key=lambda x: (x.source['id'], x.target['id']))
    nodes = Nodes()
    nodes.edges = edges

    return nodes.toJSON()


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    query = 'MATCH (n) detach delete n'
    neo4j_connect.run(query)
    return api_v1_network()


@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')


@application.route('/api/v1/topology/flows')
@login_required
def api_v1_topology_flows():
    try:
        query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
        result = neo4j_connect.run(query).data()
        flows = [format_flow(raw['r']) for raw in result]
        return json.dumps(flows)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/flows/<flow_id>')
@login_required
def api_v1_topology_get_flow(flow_id):
    query = (
        "MATCH (a:switch)-[r:flow]->(b:switch)\n"
        "WHERE r.flowid = {flow_id}\n"
        "RETURN r")
    result = neo4j_connect.run(query, flow_id=flow_id).data()
    if not result:
        return http_errors.NotFound(
                'There is no flow with flow_id={}'.format(flow_id))
    if len(result) < 2:
        return http_errors.NotFound('Flow data corrupted (too few results)')
    elif 2 < len(result):
        return http_errors.NotFound('Flow data corrupted (too many results)')

    flow_pair = [format_flow(record['r']) for record in result]
    flow_pair.sort(key=lambda x: is_forward_cookie(x['cookie']))
    flow_data = dict(zip(['reverse', 'forward'], flow_pair))

    return jsonify(flow_data)


def format_isl(link):
    """
    :param link: A valid Link returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.IslInfoData
    """
    isl = {
        'clazz': 'org.openkilda.messaging.info.event.IslInfoData',
        'latency_ns': int(link['latency']),
        'path': [{'switch_id': link['src_switch'],
                  'port_no': int(link['src_port']),
                  'seq_id': 0,
                  'segment_latency': int(link['latency'])},
                 {'switch_id': link['dst_switch'],
                  'port_no': int(link['dst_port']),
                  'seq_id': 1,
                  'segment_latency': 0}],
        'speed': link['speed'],
        'state': get_isl_state(link),
        'available_bandwidth': link['available_bandwidth'],
        'time_create': format_db_datetime(link['time_create']),
        'time_modify': format_db_datetime(link['time_modify'])
    }

    # fields that have already been used .. should find easier way to do this..
    already_used = list(isl.keys())
    for k,v in link.iteritems():
        if k not in already_used:
            isl[k] = v

    return isl


def get_isl_state(link):
    if link['status'] == 'active':
        return 'DISCOVERED'
    elif link['status'] == 'moved':
        return 'MOVED'
    else:
        return 'FAILED'


def format_switch(switch):
    """
    :param switch: A valid Switch returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.SwitchInfoData
    """
    return {
        'clazz': 'org.openkilda.messaging.info.event.SwitchInfoData',
        'switch_id': switch['name'],
        'address': switch['address'],
        'hostname': switch['hostname'],
        'state': 'ACTIVATED' if switch['state'] == 'active' else 'DEACTIVATED',
        'description': switch['description']
    }


def format_flow(raw_flow):
    flow = raw_flow.copy()

    path = json.loads(raw_flow['flowpath'])
    path['clazz'] = 'org.openkilda.messaging.info.event.PathInfoData'

    flow['flowpath'] = path

    return flow


@application.route('/api/v1/topology/links')
@login_required
def api_v1_topology_links():
    """
    :return: all isl relationships in the database
    """
    try:
        query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r"
        result = neo4j_connect.run(query).data()

        links = []
        for link in result:
            neo4j_connect.pull(link['r'])
            links.append(format_isl(link['r']))

        application.logger.info('links found %d', len(result))

        return jsonify(links)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/switches')
@login_required
def api_v1_topology_switches():
    """
    :return: all switches in the database
    """
    try:
        query = "MATCH (n:switch) RETURN n"
        result = neo4j_connect.run(query).data()

        switches = []
        for sw in result:
            neo4j_connect.pull(sw['n'])
            switches.append(format_switch(sw['n']))

        application.logger.info('switches found %d', len(result))

        return jsonify(switches)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/links/bandwidth/<src_switch>/<int:src_port>')
@login_required
def api_v1_topology_link_bandwidth(src_switch, src_port):
    query = (
        "MATCH (a:switch)-[r:isl]->(b:switch) "
        "WHERE r.src_switch = '{}' AND r.src_port = {} "
        "RETURN r.available_bandwidth").format(src_switch, int(src_port))

    return neo4j_connect.run(query).data()[0]['r.available_bandwidth']


@application.route('/api/v1/topology/routes/src/<src_switch>/dst/<dst_switch>')
@login_required
def api_v1_routes_between_nodes(src_switch, dst_switch):
    depth = request.args.get('depth') or '10'
    query = (
        "MATCH p=(src:switch{{name:'{src_switch}'}})-[:isl*..{depth}]->"
        "(dst:switch{{name:'{dst_switch}'}}) "
        "WHERE ALL(x IN NODES(p) WHERE SINGLE(y IN NODES(p) WHERE y = x)) "
        "WITH RELATIONSHIPS(p) as links "
        "WHERE ALL(l IN links WHERE l.status = 'active') "
        "RETURN links"
    ).format(src_switch=src_switch, depth=depth, dst_switch=dst_switch)

    result = neo4j_connect.run(query).data()

    paths = []
    for links in result:
        current_path = []
        for isl in links['links']:
            path_node = build_path_nodes(isl, len(current_path))
            current_path.extend(path_node)

        paths.append(build_path_info(current_path))

    return jsonify(paths)


def build_path_info(path):
    return {
        'clazz': 'org.openkilda.messaging.info.event.PathInfoData',
        'path': path
    }


def build_path_nodes(link, seq_id):
    nodes = []
    src_node = {
        'clazz': 'org.openkilda.messaging.info.event.PathNode',
        'switch_id': link['src_switch'],
        'port_no': int(link['src_port']),
        'seq_id': seq_id
    }
    nodes.append(src_node)

    dst_node = {
        'clazz': 'org.openkilda.messaging.info.event.PathNode',
        'switch_id': link['dst_switch'],
        'port_no': int(link['dst_port']),
        'seq_id': seq_id + 1
    }
    nodes.append(dst_node)
    return nodes


# FIXME(surabujin): stolen from topology-engine code, must use some shared
# codebase
def is_forward_cookie(cookie):
    return int(cookie) & 0x4000000000000000


def format_db_datetime(value):
    if not value:
        return None

    value = datetime.datetime.strptime(value, DATETIME_FORMAT)
    value = value.replace(tzinfo=pytz.utc)

    from_epoch = value - UNIX_EPOCH
    seconds = from_epoch.total_seconds()
    return seconds * 1000 + from_epoch.microseconds // 1000
