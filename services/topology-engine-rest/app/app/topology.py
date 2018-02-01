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

from app import application
from app import db
from app import utils

import sys, os
import requests
import json
import ConfigParser

import py2neo
from . import neo4j_tools

config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

neo4j_connect = neo4j_tools.connect(config)

@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    query = 'MATCH (n) return n'
    topology = []
    for record in neo4j_connect.data(query):
        record = record['n']
        relations = [
            rel['dst_switch']
            for rel in neo4j_connect.match(record, rel_type='isl')]
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
    for record in neo4j_connect.data('MATCH (n) return n'):
        source = record['n']

        for rel in neo4j_connect.match(source, rel_type='isl'):
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
        result = neo4j_connect.data(query)

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
        result = neo4j_connect.data(query)

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
        result = neo4j_connect.data(query)

        switches = []
        for sw in result:
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

    return neo4j_connect.data(query)[0]['r.available_bandwidth']
