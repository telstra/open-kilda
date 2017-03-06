from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json

@application.route('/api/v1/topology')
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


@application.route('/api/v1/network')
@login_required
def api_v1_network():
    try:
        data = {"statements":[{"statement":"MATCH path = (n)-[r]->(m) RETURN path", "resultDataContents":["graph"]}]}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        result_switches = requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        j_switches = json.loads(result_switches.text)
        return "test"
    except Exception as e:
        return "error: {}".format(str(e))

@application.route('/topology/chord', methods=['GET'])
@login_required
def topology_chord():
    return render_template('topologychord.html')

@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')