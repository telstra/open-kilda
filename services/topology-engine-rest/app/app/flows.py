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
import random
import time
import uuid
import ConfigParser

from kafka import KafkaConsumer, KafkaProducer

from . import neo4j_tools


config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

group = config.get('kafka', 'consumer.group')
topic = config.get('kafka', 'kafka.topic.flow')

try:
    environment_naming_prefix = config.get('kafka',
                                           'environment.naming.prefix')
    if environment_naming_prefix.strip():
        group = '_'.join([environment_naming_prefix, group])
        topic = '_'.join([environment_naming_prefix, topic])
except ConfigParser.NoOptionError:
    pass

bootstrap_servers_property = config.get('kafka', 'bootstrap.servers')
bootstrap_servers = [x.strip() for x in bootstrap_servers_property.split(',')]

neo4j_connect = neo4j_tools.connect(config)


class Flow(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Message(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


def build_ingress_flow(expandedRelationships, src_switch, src_port, src_vlan, bandwidth, transit_vlan, flow_id, outputAction):
    match = src_port
    for relationship in expandedRelationships:
        if relationship['data']['src_switch'] == src_switch:
            action = relationship['data']['src_port']
    flow = Flow()
    flow.command = "install_ingress_flow"
    flow.destination = "CONTROLLER"
    flow.cookie = flow_id
    flow.switch_id = src_switch
    flow.input_port= int(src_port)
    flow.output_port = action
    flow.input_vlan_id = int(src_vlan)
    flow.transit_vlan_id = int(transit_vlan)
    flow.output_vlan_type = outputAction
    flow.bandwidth = bandwidth
    flow.meter_id = assign_meter_id()
    return flow


def build_egress_flow(expandedRelationships, dst_switch, dst_port, dst_vlan, transit_vlan, flow_id, outputAction):
    action = dst_port
    for relationship in expandedRelationships:
        if relationship['data']['dst_switch'] == dst_switch:
            match = relationship['data']['dst_port']
    flow = Flow()
    flow.command = "install_egress_flow"
    flow.destination = "CONTROLLER"
    flow.cookie = flow_id
    flow.switch_id = dst_switch
    flow.input_port = int(match)
    flow.output_port = int(dst_port)
    flow.transit_vlan_id = int(transit_vlan)
    flow.output_vlan_id = int(dst_vlan)
    flow.output_vlan_type = outputAction
    return flow


def build_intermediate_flows(expandedRelationships, transit_vlan, i, flow_id):
    # output action is always NONE for transit vlan id
    match = expandedRelationships[i]['data']['dst_port']
    action = expandedRelationships[i+1]['data']['src_port']
    switch = expandedRelationships[i]['data']['dst_switch']
    flow = Flow()
    flow.command = "install_transit_flow"
    flow.destination = "CONTROLLER"
    flow.cookie = flow_id
    flow.switch_id = switch
    flow.input_port = int(match)
    flow.output_port = int(action)
    flow.transit_vlan_id = int(transit_vlan)
    return flow


def build_one_switch_flow(switch, src_port, src_vlan, dst_port, dst_vlan, bandwidth, flow_id, outputAction):
    flow = Flow()
    flow.command = "install_one_switch_flow"
    flow.destination = "CONTROLLER"
    flow.cookie = flow_id
    flow.switch_id = switch
    flow.input_port = int(src_port)
    flow.output_port = int(dst_port)
    flow.input_vlan_id = int(src_vlan)
    flow.output_vlan_id = int(dst_vlan)
    flow.bandwidth = bandwidth
    flow.input_meter_id = assign_meter_id()
    flow.output_meter_id = assign_meter_id()
    flow.output_vlan_type = outputAction
    return flow

def build_delete_flow(switch, flow_id):
    flow = Flow()
    flow.command = "delete_flow"
    flow.destination = "CONTROLLER"
    flow.cookie = flow_id
    flow.switch_id = switch
    return flow

def expand_relationships(relationships):
    fullRelationships = []
    for relationship in relationships:
        fullRelationships.append(json.loads((requests.get(relationship, auth=('neo4j', 'temppass'))).text))
    return fullRelationships

def get_relationships(src_switch, src_port, dst_switch, dst_port):
    query = (
        "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) where ALL(x in nodes(p) "
        "WHERE x.state = 'active') "
        "RETURN p").format(src_switch, dst_switch)
    match = neo4j_connect.run(query)
    if match:
        return match[0]['relationships']
    return []


def assign_transit_vlan():
    return random.randrange(99, 4000,1)

def assign_flow_id():
    return "123"

def assign_meter_id():
    # zero means meter should not be actually installed on switch
    # zero value should be used only for software switch based testing
    return 0

def choose_output_action(input_vlan_id, output_vlan_id):
    # TODO: move to Storm Flow Topology
    if not input_vlan_id or input_vlan_id == 0:
        if not output_vlan_id or output_vlan_id == 0:
            output_action_type = "NONE"
        else:
            output_action_type = "PUSH"
    else:
        if not output_vlan_id or output_vlan_id == 0:
            output_action_type = "POP"
        else:
            output_action_type = "REPLACE"
    return output_action_type

def api_v1_topology_get_one_switch_flows(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan, bandwidth, flow_id):
    forwardOutputAction = choose_output_action(int(src_vlan), int(dst_vlan))
    reverseOutputAction = choose_output_action(int(dst_vlan), int(src_vlan))
    return [[build_one_switch_flow(src_switch, src_port, src_vlan, dst_port, dst_vlan, bandwidth, flow_id, forwardOutputAction)],
            [build_one_switch_flow(dst_switch, dst_port, dst_vlan, src_port, src_vlan, bandwidth, flow_id, reverseOutputAction)]]

def api_v1_topology_get_path(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan,
                             bandwidth, transit_vlan, flow_id):
    relationships = get_relationships(src_switch, src_port, dst_switch, dst_port)
    outputAction = choose_output_action(int(src_vlan), int(dst_vlan))
    if relationships:
        expandedRelationships = expand_relationships(relationships)
        flows = []
        flows.append(build_ingress_flow(expandedRelationships, src_switch, src_port, src_vlan, bandwidth, transit_vlan, flow_id, outputAction))
        intermediateFlowCount = len(expandedRelationships) - 1
        i = 0
        while i < intermediateFlowCount:
            flows.append(build_intermediate_flows(expandedRelationships, transit_vlan, i, flow_id))
            i += 1
        flows.append(build_egress_flow(expandedRelationships, dst_switch, dst_port, dst_vlan, transit_vlan, flow_id, outputAction))
        return flows
    else:
        return False


@application.route('/api/v1/health-check', methods=["GET"])
def api_v1_health_check():
    return '{"status": "ok"}'

@application.route('/api/v1/flow/<flowid>', methods=["GET", "DELETE"])
#@login_required
def api_v1_flow(flowid):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} r"

    if request.method == 'GET':
        result = neo4j_connect.run(query.format(flowid, "return")).data()
        status = result['status']
        del result['status']
        result['state'] = status
    if request.method == 'DELETE':
        producer = KafkaProducer(bootstrap_servers=bootstrap_servers)
        switches =  neo4j_connect.run("MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) return r.flowpath limit 1".format(flowid)).evaluate()
        for switch in switches:
            message = Message()
            message.data = build_delete_flow(switch, str(flowid))
            message.type = "COMMAND"
            message.timestamp = 42
            kafkamessage = b'{}'.format(message.toJSON())
            print 'topic: {}, message: {}'.format(topic, kafkamessage)
            messageresult = producer.send(topic, kafkamessage)
            messageresult.get(timeout=5)
        result = neo4j_connect.run(query.format(flowid, "delete")).data()
    return json.dumps(result)


@application.route('/api/v1/flow', methods=["POST"])
#@login_required
def api_v1_create_flow():
    if request.method == 'POST':
        producer = KafkaProducer(bootstrap_servers=bootstrap_servers)
        content = json.loads('{}'.format(request.data))
        flowID = assign_flow_id()

        if content['src_switch'] == content['dst_switch']:
            allflows = api_v1_topology_get_one_switch_flows(
                content['src_switch'], content['src_port'], content['src_vlan'],
                content['dst_switch'], content['dst_port'], content['dst_vlan'],
                content['bandwidth'], flowID)
        else:
            transitVlanForward = assign_transit_vlan()
            forwardFlows = api_v1_topology_get_path(
                content['src_switch'], content['src_port'], content['src_vlan'],
                content['dst_switch'], content['dst_port'], content['dst_vlan'],
                content['bandwidth'], transitVlanForward, flowID)

            transitVlanReturn = assign_transit_vlan()
            reverseFlows = api_v1_topology_get_path(
                content['dst_switch'], content['dst_port'], content['dst_vlan'],
                content['src_switch'], content['src_port'], content['src_vlan'],
                content['bandwidth'], transitVlanReturn, flowID)

            allflows = [forwardFlows, reverseFlows]

            if not forwardFlows or not reverseFlows:
                response = {"result": "failed", "message": "unable to find valid path in the network"}
                return json.dumps(response)

        forwardFlowSwitches = [str(f.switch_id) for f in forwardFlows]
        reverseFlowSwitches = [str(f.switch_id) for f in reverseFlows]


        for flows in allflows:
            for flow in flows:
                message = Message()
                message.data = flow
                message.type = "COMMAND"
                message.timestamp = 42
                kafkamessage = b'{}'.format(message.toJSON())
                print 'topic: {}, message: {}'.format(topic, kafkamessage)
                messageresult = producer.send(topic, kafkamessage)
                result = messageresult.get(timeout=5)

        a_switchNode = neo4j_connect.find_one('switch', property_key='name', property_value='{}'.format(content['src_switch']))
        b_switchNode = neo4j_connect.find_one('switch', property_key='name', property_value='{}'.format(content['dst_switch']))

        if not a_switchNode or not b_switchNode:
            return '{"result": "failed"}'

        pathQuery = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) MERGE (u)-[:flow {{flowid:'{}', src_port: '{}', dst_port: '{}', src_switch: '{}', dst_switch: '{}', flowpath: {}}}]->(r)"

        pathForwardQuery = pathQuery.format(a_switchNode['name'], b_switchNode['name'], flowID, content['src_port'], content['dst_port'], content['src_switch'], content['dst_switch'], str(forwardFlowSwitches))
        pathReverseQuery = pathQuery.format(b_switchNode['name'], a_switchNode['name'], flowID, content['dst_port'], content['src_port'], content['dst_switch'], content['src_switch'], str(reverseFlowSwitches))

        neo4j_connect.run(pathForwardQuery)
        neo4j_connect.run(pathReverseQuery)

        response = {"result": "sucessful", "flowID": flowID}
        return json.dumps(response)


@application.route('/api/v1/push/flows', methods=["PUT"])
#@login_required
def api_v1_push_flows():
    return jsonify(successes=0, failures=0, messages=["come back later"])


