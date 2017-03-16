from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json


def get_reciprocal_relationship(relationshipURL):
    reciprocalRelationship = {}
    resultRelationship = requests.get(relationshipURL, auth=('neo4j', 'temppass'))
    jResultRelationship = json.loads(resultRelationship.text)
    reciprocalRelationship['src_port'] = jResultRelationship['data']['dst_port']
    reciprocalRelationship['src_switch'] = jResultRelationship['data']['dst_switch']
    reciprocalRelationship['dst_port'] = jResultRelationship['data']['src_port']
    reciprocalRelationship['dst_switch'] = jResultRelationship['data']['src_switch']
    query = "MATCH p=()-[r:isl{{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]->() RETURN p".format(reciprocalRelationship['src_switch'], reciprocalRelationship['src_port'], reciprocalRelationship['dst_switch'], reciprocalRelationship['dst_port'])
    data = {'query' : query} 
    resultReciprocalRelationship = requests.post('http://neo4j:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
    jResultReciprocalRelationship = json.loads(resultReciprocalRelationship.text)
    return jResultReciprocalRelationship['data'][0][0]['relationships'][0]

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
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

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


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    try:
        data = {'query' : 'MATCH (n) detach delete n'}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        return api_v1_network()
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/path/<src_switch>/<src_port>/<dst_switch>/<dst_port>')
@login_required
def api_v1_topology_path(src_switch, src_port, dst_switch, dst_port):

    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) RETURN p".format(src_switch,dst_switch)
    data = {'query' : query}    
    resultPath = requests.post('http://neo4j:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
    jForwardPath = json.loads(resultPath.text)


    forwardPath = []
    reversePath = []

    for relationshipURL in jForwardPath['data'][0][0]['relationships']:
        forwardPath.append(relationshipURL)
        reversePath.append(get_reciprocal_relationship(relationshipURL))

    if len(forwardPath) == len(reversePath):
        pathLength = len(forwardPath)

    i = 0

    print forwardPath
    print reversePath


    forwardFlows = []
    reverseFlows = []

    while i <= pathLength:
        forwardFlow = {}
        reverseFlow = {}
        if i == 0:
            resultForwardRelationship = requests.get(forwardPath[i], auth=('neo4j', 'temppass'))
            jResultForwardRelationship = json.loads(resultForwardRelationship.text)
            resultReverseRelationship = requests.get(reversePath[i], auth=('neo4j', 'temppass'))
            jResultReverseRelationship = json.loads(resultReverseRelationship.text)
            forwardFlow['switch'] = src_switch
            forwardFlow['match'] = "in_port: {}".format(src_port)
            forwardFlow['action'] = "output: {}".format(jResultForwardRelationship['data']['src_port'])
            reverseFlow['switch'] = src_switch
            reverseFlow['match'] = "in_port: {}".format(jResultReverseRelationship['data']['dst_port'])
            reverseFlow['action'] = "output: {}".format(src_port)
        elif i == pathLength:
            print i
            resultForwardRelationship = requests.get(forwardPath[i-1], auth=('neo4j', 'temppass'))
            jResultForwardRelationship = json.loads(resultForwardRelationship.text)
            resultReverseRelationship = requests.get(reversePath[i-1], auth=('neo4j', 'temppass'))
            jResultReverseRelationship = json.loads(resultReverseRelationship.text)
            forwardFlow['switch'] = dst_switch
            forwardFlow['match'] = "in_port: {}".format(jResultForwardRelationship['data']['dst_port']) #needs and update
            forwardFlow['action'] = "output: {}".format(dst_port) 
            reverseFlow['switch'] = dst_switch
            reverseFlow['match'] = "in_port: {}".format(dst_port)
            reverseFlow['action'] = "output: {}".format(jResultReverseRelationship['data']['src_port']) #needs and update
        else:
            resultForwardRelationshipMatch = requests.get(forwardPath[i-1], auth=('neo4j', 'temppass'))
            jResultForwardRelationshipMatch = json.loads(resultForwardRelationshipMatch.text)
            resultForwardRelationshipAction = requests.get(forwardPath[i], auth=('neo4j', 'temppass'))
            jResultForwardRelationshipAction = json.loads(resultForwardRelationshipAction.text)
            resultReverseRelationshipMatch = requests.get(reversePath[i], auth=('neo4j', 'temppass'))
            jResultReverseRelationshipMatch = json.loads(resultReverseRelationshipMatch.text)
            resultReverseRelationshipAction = requests.get(reversePath[i-1], auth=('neo4j', 'temppass'))
            jResultReverseRelationshipAction = json.loads(resultReverseRelationshipAction.text)
            forwardFlow['switch'] = jResultForwardRelationshipMatch['data']['dst_switch']
            forwardFlow['match'] = "in_port: {}".format(jResultForwardRelationshipMatch['data']['dst_port'])
            forwardFlow['action'] = "output: {}".format(jResultForwardRelationshipAction['data']['src_port'])
            reverseFlow['switch'] = jResultReverseRelationshipAction['data']['src_switch']
            reverseFlow['match'] = "in_port: {}".format(jResultReverseRelationshipMatch['data']['dst_port'])
            reverseFlow['action'] = "output: {}".format(jResultReverseRelationshipAction['data']['src_port'])
        forwardFlows.append(forwardFlow)
        reverseFlows.append(reverseFlow)
        i += 1
    flows = [forwardFlows, reverseFlows]
    
    return json.dumps(flows)

    #return json.dumps(flows, indent=4, sort_keys=True)
    #return Response(response=json.dumps(flows), status=200, mimetype='application/json')

@application.route('/topology/chord', methods=['GET'])
@login_required
def topology_chord():
    return render_template('topologychord.html')

@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')