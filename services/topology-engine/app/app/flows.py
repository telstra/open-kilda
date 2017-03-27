from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json

from kafka import KafkaConsumer, KafkaProducer
neo4jhost = "neo4j"
bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'
producer = KafkaProducer(bootstrap_servers=bootstrapServer)

class Flow(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

def build_ingress_flow(expandedRelationships, src_switch, src_port, src_vlan, transitVlan):
    match = src_port
    for relationship in expandedRelationships:
        if relationship['data']['src_switch'] == src_switch:
            action = relationship['data']['src_port']
    
    flow = Flow()
    flow.command = "install_ingress_flow"
    flow.destination = "CONTROLLER"
    flow.flow_name = "test_flow"
    flow.switch_id = src_switch
    flow.input_port= int(src_port)
    flow.output_port = action
    flow.input_vlan_id = int(src_vlan)
    flow.transit_vlan_id = int(transitVlan)
    flow.bandwidth = 10000

    return flow

def build_egress_flow(expandedRelationships, dst_switch, dst_port, dst_vlan, transitVlan):
    action = dst_port
    for relationship in expandedRelationships:
        if relationship['data']['dst_switch'] == dst_switch:
            match = relationship['data']['src_port']
    flow = Flow()
 
    flow.command = "install_egress_flow"
    flow.destination = "CONTROLLER"
    flow.flow_name = "test_flow"
    flow.switch_id = dst_switch
    flow.input_port = int(match)
    flow.output_port = int(dst_port)
    flow.transit_vlan_id = int(transitVlan)

    return flow

def build_intermediate_flows(expandedRelationships, transitVlan, i):
    match = expandedRelationships[i]['data']['dst_port']
    action = expandedRelationships[i+1]['data']['src_port']
    switch = expandedRelationships[i]['data']['dst_switch']
    flow = Flow()

    flow.command = "install_transit_flow"
    flow.destination = "CONTROLLER"
    flow.flow_name = "test_flow"
    flow.switch_id = switch
    flow.input_port = int(match)
    flow.output_port = int(action)
    flow.transit_vlan_id = int(transitVlan)

    return flow

def expand_relationships(relationships):
    fullRelationships = []
    for relationship in relationships:
        fullRelationships.append(json.loads((requests.get(relationship, auth=('neo4j', 'temppass'))).text))
    return fullRelationships

def get_relationships(src_switch, src_port, dst_switch, dst_port):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) RETURN p".format(src_switch,dst_switch)
    data = {'query' : query}    
    resultPath = requests.post('http://{}:7474/db/data/cypher'.format(neo4jhost), data=data, auth=('neo4j', 'temppass'))
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





@application.route('/api/v1/flow', methods=["POST"])
#@login_required
def api_v1_topology_path():
    content = json.loads('{}'.format(request.data))
    print content

    src_switch = content['src_switch']
    src_port = content['src_port']
    src_vlan = content['src_vlan']
    dst_switch = content['dst_switch']
    dst_port = content['dst_port']
    dst_vlan = content['dst_vlan']

    '''
    src_switch = "00:00:00:00:00:00:00:01"
    src_port = "11"
    src_vlan = "111"
    dst_switch = "00:00:00:00:00:00:00:05"
    dst_port = "55"
    dst_vlan = "555"
    '''



    forwardFlows = api_v1_topology_get_path(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan)
    reverseFlows = api_v1_topology_get_path(dst_switch, dst_port, dst_vlan, src_switch, src_port, src_vlan)

    allflows = [forwardFlows, reverseFlows]

    for flows in allflows:
        for flow in flows:
            producer.send(topic, b'{}'.format(flow.toJSON()))

    return '{"result": "sucessful"}'