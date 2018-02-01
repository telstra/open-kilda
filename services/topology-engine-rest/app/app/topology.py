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

from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response, jsonify
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user
from py2neo import Graph

from app import application
from app import db
from app import utils

import sys, os
import requests
import json
import ConfigParser

config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

NEO4J_HOST = os.environ['neo4jhost'] or config.get('neo4j', 'host')
NEO4J_USER = os.environ['neo4juser'] or config.get('neo4j', 'user')
NEO4J_PASS = os.environ['neo4jpass'] or config.get('neo4j', 'pass')
NEO4J_BOLT = os.environ['neo4jbolt'] or config.get('neo4j', 'bolt')
AUTH = (NEO4J_USER, NEO4J_PASS)

@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    try:
        data = {'query' : 'MATCH (n) return n'}

        result_switches = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        j_switches = json.loads(result_switches.text)
        nodes = []
        topology = {}
        for n in j_switches['data']:
            for r in n:
                node = {}
                node['name'] = (r['data']['name'])
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=AUTH)
                j_paths = json.loads(result_relationships.text)
                outgoing_relationships = []
                for j_path in j_paths:
                    if j_path['type'] == u'isl':
                        outgoing_relationships.append(j_path['data']['dst_switch'])
                    outgoing_relationships.sort()
                    node['outgoing_relationships'] = outgoing_relationships
            nodes.append(node)
        topology['nodes'] = nodes
        return str(json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


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
    data = {'query' : 'MATCH (n) return n'}
    result_switches = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
    j_switches = json.loads(result_switches.text)
    nodes = Nodes()
    nodes.edges = []
    for n in j_switches['data']:
        for r in n:
            result_relationships = requests.get(str(r['outgoing_relationships']), auth=auth)
            j_paths = json.loads(result_relationships.text)
            outgoing_relationships = []
            for j_path in j_paths:
                target = Link()
                if j_path['type'] == u'isl':
                    edge = Edge()
                    source = Link()
                    source.label = r['data']['name']
                    source.id = r['metadata']['id']
                    dest_node = requests.get(str(j_path['end']), auth=auth)
                    j_dest_node = json.loads(dest_node.text)
                    target.label = j_path['data']['dst_switch']
                    target.id = j_dest_node['metadata']['id']
                    edge.value = "{} to {}".format(source.label, target.label)
                    edge.source = source
                    edge.target = target
                    nodes.edges.append(edge)
    return nodes.toJSON()


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    try:
        data = {'query' : 'MATCH (n) detach delete n'}
        requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        return api_v1_network()
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')


def create_p2n_driver():
    graph = Graph("http://{}:{}@{}:7474/db/data/".format(
        NEO4J_USER, NEO4J_PASS, NEO4J_HOST))
    return graph

graph = create_p2n_driver()


@application.route('/api/v1/topology/flows')
@login_required
def api_v1_topology_flows():
    try:
        query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
        result = graph.data(query)

        flows = []
        for data in result:
            path = json.loads(data['r']['flowpath'])
            flow = json.loads(json.dumps(data['r'],
                                         default=lambda o: o.__dict__,
                                         sort_keys=True))
            flow['flowpath'] = path
            flows.append(flow)

        return str(json.dumps(flows, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


def format_isl(link):
    """
    :param link: A valid Link returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.IslInfoData
    """
    return {
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
        'state': 'DISCOVERED' if link['status'] == 'active' else 'FAILED',
        'available_bandwidth': link['available_bandwidth']
    }

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


@application.route('/api/v1/topology/links')
@login_required
def api_v1_topology_links():
    """
    :return: all isl relationships in the database
    """
    try:
        query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r"
        result = graph.data(query)

        links = []
        for link in result:
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
        result = graph.data(query)

        switches = []
        for sw in result:
            switches.append(format_switch(sw['n']))

        application.logger.info('switches found %d', len(result))

        return jsonify(switches)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/links/bandwidth/<src_switch>/<src_port>')
@login_required
def api_v1_topology_link_bandwidth(src_switch, src_port):
    try:
        data = {'query': "MATCH (a:switch)-[r:isl]->(b:switch) "
                         "WHERE r.src_switch = '{}' AND r.src_port = {} "
                         "RETURN r.available_bandwidth".format(
                            str(src_switch), int(src_port))}

        response = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        data = json.loads(response.text)
        bandwidth = data['data'][0][0]

        return str(bandwidth)

    except Exception as e:
        return "error: {}".format(str(e))
