from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json

class Flow:
    def __init__(self, switch, match, action, src_vlan, dst_vlan):
        self.switch = switch
        self.match = "in_port: {}".format(match)
        self.action = "output: {}".format(action)
        self.src_vlan = "{}".format(src_vlan)
        self.dst_vlan = "{}".format(dst_vlan)

    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, 
            sort_keys=False, indent=4)

def build_ingress_flow(expandedRelationships, src_switch, src_port, src_vlan, transitVlan):
    match = src_port
    for relationship in expandedRelationships:
        if relationship['data']['src_switch'] == src_switch:
            action = relationship['data']['src_port']
    flow = Flow(src_switch, match, action, src_vlan, transitVlan)
    return flow

def build_egress_flow(expandedRelationships, dst_switch, dst_port, dst_vlan, transitVlan):
    action = dst_port
    for relationship in expandedRelationships:
        if relationship['data']['dst_switch'] == dst_switch:
            match = relationship['data']['src_port']
    flow = Flow(dst_switch, match, action, transitVlan, dst_vlan)
    return flow

def build_intermediate_flows(expandedRelationships, transitVlan, i):
    match = expandedRelationships[i]['data']['dst_port']
    action = expandedRelationships[i+1]['data']['src_port']
    switch = expandedRelationships[i]['data']['dst_switch']
    flow = Flow(switch, match, action, transitVlan, transitVlan)
    return flow

def expand_relationships(relationships):
    fullRelationships = []
    for relationship in relationships:
        fullRelationships.append(json.loads((requests.get(relationship, auth=('neo4j', 'temppass'))).text))
    return fullRelationships

def get_relationships(src_switch, src_port, dst_switch, dst_port):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) RETURN p".format(src_switch,dst_switch)
    data = {'query' : query}    
    resultPath = requests.post('http://neo4j:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
    jPath = json.loads(resultPath.text)
    return jPath['data'][0][0]['relationships']

def assign_transit_vlan():
    return 666

def api_v1_topology_get_path(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan):
    transitVlan = assign_transit_vlan ()
    relationships = get_relationships(src_switch, src_port, dst_switch, dst_port)
    expandedRelationships = expand_relationships(relationships)
    flows = []
    flows.append(build_ingress_flow(expandedRelationships, src_switch, src_port, src_vlan, transitVlan))
    intermediateFlowCount = len(expandedRelationships) - 1
    i = 0
    while i < intermediateFlowCount:
        flows.append(build_intermediate_flows(expandedRelationships, transitVlan, i))
        i += 1
    flows.append(build_egress_flow(expandedRelationships, dst_switch, dst_port, dst_vlan, transitVlan))
    return flows



@application.route('/api/v1/flow')
@login_required
def api_v1_topology_path():
    
    src_switch = "00:00:00:00:00:00:00:01"
    src_port = "11"
    src_vlan = "111"
    dst_switch = "00:00:00:00:00:00:00:05"
    dst_port = "55"
    dst_vlan = "555"

    forwardFlows = api_v1_topology_get_path(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan)
    reverseFlows = api_v1_topology_get_path(dst_switch, dst_port, dst_vlan, src_switch, src_port, src_vlan)

    allflows = [forwardFlows, reverseFlows]

    for flows in allflows:
        for flow in flows:
            print flow.toJSON()
    
    return '{"flow_status": "created"}'