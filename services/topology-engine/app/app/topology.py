from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json

@application.route('/api/v1/topology/chord')
@login_required
def api_v1_topology():
    try:
        data = {'query' : 'MATCH (n) return n'}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        result_switches = requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        j_switches = json.loads(result_switches.text)
        nodes = []
        topology = {}
        for n in j_switches['data']:
            for r in n:
                node = {} 
                node['name'] = (r['data']['name'])
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=auth)
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


@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    try:
        data = {'query' : 'MATCH (n) return n'}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        result_switches = requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        j_switches = json.loads(result_switches.text)       
        nodes = []
        links = []
        topology = {}
        for n in j_switches['data']:
            for r in n:
                node = {}
                node['name'] = str((r['data']['name']))
                nodes.append(node)
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=('neo4j', 'temppass'))
                j_paths = json.loads(result_relationships.text)
                for j_path in j_paths:
                    if j_path['type'] == u'isl':
                        link = {}
                        link['src_switch'] = str(j_path['data']['src_switch'])
                        link['src_port'] = str(j_path['data']['src_port'])
                        link['dst_switch'] = str(j_path['data']['dst_switch'])
                        link['dst_port'] = str(j_path['data']['dst_port'])
                        links.append(link)
        topology['nodes'] = nodes
        topology['links'] = links
        return str(json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/path/<src_switch>/<src_port>/<dst_switch>/<dst_port>')
@login_required
def api_v1_topology_path(src_switch, src_port, dst_switch, dst_port):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..15]->(b)) RETURN p".format(src_switch,dst_switch)
    data = {'query' : query}
    auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
    result_path = requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
    j_path = json.loads(result_path.text)

    return json.dumps(j_path['data'])

@application.route('/topology/chord', methods=['GET'])
@login_required
def topology_chord():
    return render_template('topologychord.html')

@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')