import sys, os
import requests
import json
import random


neo4jhost = os.environ['neo4jhost']
neo4juser = os.environ['neo4juser']
neo4jpass = os.environ['neo4jpass']


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
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = assign_cookie()
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
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = assign_cookie()
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
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = assign_cookie()
    flow.switch_id = switch
    flow.input_port = int(match)
    flow.output_port = int(action)
    flow.transit_vlan_id = int(transit_vlan)
    return flow


def build_one_switch_flow(switch, src_port, src_vlan, dst_port, dst_vlan, bandwidth, flow_id, outputAction):
    flow = Flow()
    flow.command = "install_one_switch_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = assign_cookie()
    flow.switch_id = switch
    flow.input_port = int(src_port)
    flow.output_port = int(dst_port)
    flow.input_vlan_id = int(src_vlan)
    flow.output_vlan_id = int(dst_vlan)
    flow.output_vlan_type = outputAction
    flow.bandwidth = bandwidth
    flow.src_meter_id = assign_meter_id()
    flow.dst_meter_id = assign_meter_id()

    return flow


def build_delete_flow(switch, flow_id):
    flow = Flow()
    flow.command = "delete_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = assign_cookie()
    flow.switch_id = switch
    flow.meter_id = assign_meter_id()
    return flow


def expand_relationships(relationships):
    fullRelationships = []
    for relationship in relationships:
        fullRelationships.append(json.loads((requests.get(relationship, auth=('neo4j', 'temppass'))).text))
    return fullRelationships


def get_relationships(src_switch, src_port, dst_switch, dst_port):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) where ALL(x in nodes(p) WHERE x.state = 'active') RETURN p".format(src_switch, dst_switch)
    data = {'query' : query}
    resultPath = requests.post('http://{}:7474/db/data/cypher'.format(neo4jhost), data=data, auth=(neo4juser, neo4jpass))
    jPath = json.loads(resultPath.text)
    if jPath['data']:
        return jPath['data'][0][0]['relationships']
    else:
        return False


def assign_transit_vlan():
    return random.randrange(99, 4000, 1)


def assign_cookie():
    return 2


def assign_meter_id():
    return 0


def choose_output_action(input_vlan_id, output_vlan_id):
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


def flow_response(flow):
    source = {'switch-id': flow['src_switch'], 'port-id': flow['src_port'], 'vlan-id': flow['src_vlan']}
    destination = {'switch-id': flow['dst_switch'], 'port-id': flow['dst_port'], 'vlan-id': flow['dst_vlan']}
    response = {'last-updated': flow['last_updated'], 'maximum-bandwidth': flow['bandwidth'], 'flowid': flow['flowid'],
                'description': flow['description'], 'cookie': flow['cookie'], 'source': source, 'destination': destination}
    return response


def prepare_flows(content, source, destination, transit_vlan_forward, transit_vlan_reverse):

    if source['switch-id'] == destination['switch-id']:
        print "Create one switch flow"

        all_flows = api_v1_topology_get_one_switch_flows(
            source['switch-id'], source['port-id'], source['vlan-id'],
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            content['maximum-bandwidth'], content['flowid'])
    else:
        print "Create flow"

        forward_flows = api_v1_topology_get_path(
            source['switch-id'], source['port-id'], source['vlan-id'],
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            content['maximum-bandwidth'], transit_vlan_forward, content['flowid'])

        reverse_flows = api_v1_topology_get_path(
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            source['switch-id'], source['port-id'], source['vlan-id'],
            content['maximum-bandwidth'], transit_vlan_reverse, content['flowid'])

        all_flows = [forward_flows, reverse_flows]

    forward_flow_switches = [str(f.switch_id) for f in forward_flows]
    reverse_flow_switches = [str(f.switch_id) for f in reverse_flows]

    return all_flows, forward_flow_switches, reverse_flow_switches
