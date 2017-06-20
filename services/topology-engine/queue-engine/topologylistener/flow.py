import os
import re
import time
import datetime
import requests
import json
from kafka import KafkaProducer

import db


__all__ = ['allocate_cookie', 'deallocate_cookie', 'allocate_transit_vlan_id', 'deallocate_transit_vlan_id',
           'find_flow_by_id', 'store_flows', 'find_nodes', 'find_flow_relationships_ids', 'find_flow_path',
           'delete_flows_from_database_by_relationship_ids', 'delete_flows_from_database_by_flow_id', 'create_flows',
           'send_install_commands', 'send_remove_commands', 'send_message', 'flow_response', 'graph']


neo4j_host = os.environ['neo4jhost']
neo4j_user = os.environ['neo4juser']
neo4j_pass = os.environ['neo4jpass']
graph = db.create_p2n_driver()
auth = (neo4j_user, neo4j_pass)
bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'
producer = KafkaProducer(bootstrap_servers=bootstrapServer)
cookies = range(0xFFFE, 0x0000, -1)
transit_vlan_ids = range(4094, 1, -1)


def get_flows():
    query = "MATCH (a:switch)-[r:flow ]->(b:switch) {} r"
    return graph.run(query.format("return")).data()


def allocate_cookie(cookie=None):
    try:
        if cookie:
            cookies.remove(cookie)
        else:
            return cookies.pop()
    except ValueError:
        pass


def deallocate_cookie(cookie):
    cookies.append(cookie)


def allocate_transit_vlan_id(transit_vlan_id=None):
    try:
        if transit_vlan_id:
            transit_vlan_ids.remove(transit_vlan_id)
        else:
            return transit_vlan_ids.pop()
    except ValueError:
        pass


def deallocate_transit_vlan_id(transit_vlan_id):
    transit_vlan_ids.append(int(transit_vlan_id))


def is_forward_cookie(cookie):
    return int(cookie) & 0x4000000000000000


def is_reverse_cookie(cookie):
    return int(cookie) & 0x2000000000000000


def forward_cookie(cookie):
    return int(cookie) | 0x4000000000000000


def reverse_cookie(cookie):
    return int(cookie) | 0x2000000000000000


def cookie_value(cookie):
    return int(cookie) & 0x00000000FFFFFFFF


def init_resources():
    flows = get_flows()
    for flow in flows:
        cookie = cookie_value(int(flow['r']['cookie']))
        transit_vlan_id = int(flow['r']['transit_vlan'])

        print "Reallocation of cookie={} vlan_id={} for flow={}".format(cookie, transit_vlan_id, flow['r']['flowid'])

        allocate_cookie(cookie)
        allocate_transit_vlan_id(transit_vlan_id)


init_resources()


class Flow(object):
    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


def build_ingress_flow(expanded_relationships, src_switch, src_port, src_vlan, bandwidth, transit_vlan,
                       flow_id, output_action, cookie, meter_id=0):
    # match = src_port
    for relationship in expanded_relationships:
        if relationship['data']['src_switch'] == src_switch:
            action = relationship['data']['src_port']

    flow = Flow()
    flow.command = "install_ingress_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = int(cookie)
    flow.switch_id = src_switch
    flow.input_port= int(src_port)
    flow.output_port = action
    flow.input_vlan_id = int(src_vlan)
    flow.transit_vlan_id = int(transit_vlan)
    flow.output_vlan_type = output_action
    flow.bandwidth = int(bandwidth)
    flow.meter_id = int(meter_id)

    return flow


def build_egress_flow(expanded_relationships, dst_switch, dst_port, dst_vlan, transit_vlan,
                      flow_id, output_action, cookie):
    # action = dst_port
    for relationship in expanded_relationships:
        if relationship['data']['dst_switch'] == dst_switch:
            match = relationship['data']['dst_port']

    flow = Flow()
    flow.command = "install_egress_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = int(cookie)
    flow.switch_id = dst_switch
    flow.input_port = int(match)
    flow.output_port = int(dst_port)
    flow.transit_vlan_id = int(transit_vlan)
    flow.output_vlan_id = int(dst_vlan)
    flow.output_vlan_type = output_action

    return flow


def build_intermediate_flows(expanded_relationships, transit_vlan, i, flow_id, cookie):
    # output action is always NONE for transit vlan id
    match = expanded_relationships[i]['data']['dst_port']
    action = expanded_relationships[i+1]['data']['src_port']
    switch = expanded_relationships[i]['data']['dst_switch']

    flow = Flow()
    flow.command = "install_transit_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = int(cookie)
    flow.switch_id = switch
    flow.input_port = int(match)
    flow.output_port = int(action)
    flow.transit_vlan_id = int(transit_vlan)

    return flow


def build_one_switch_flow(switch, src_port, src_vlan, dst_port, dst_vlan, bandwidth,
                          flow_id, output_action, cookie, forward_meter_id=0, reverse_meter_id=0):
    flow = Flow()
    flow.command = "install_one_switch_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = int(cookie)
    flow.switch_id = switch
    flow.input_port = int(src_port)
    flow.output_port = int(dst_port)
    flow.input_vlan_id = int(src_vlan)
    flow.output_vlan_id = int(dst_vlan)
    flow.output_vlan_type = output_action
    flow.bandwidth = int(bandwidth)
    flow.src_meter_id = int(forward_meter_id)
    flow.dst_meter_id = int(reverse_meter_id)

    return flow


def delete_flow(switch, flow_id, cookie, meter_id=0):
    flow = Flow()
    flow.command = "delete_flow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = int(cookie)
    flow.switch_id = switch
    flow.meter_id = int(meter_id)

    return flow


def expand_relationships(relationships):
    full_relationships = []
    for relationship in relationships:
        full_relationships.append(json.loads((requests.get(relationship, auth=auth)).text))
    return full_relationships


def get_relationships(src_switch, dst_switch):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), " \
            "p = shortestPath((a)-[:isl*..100]->(b)) " \
            "where ALL(x in nodes(p) WHERE x.state = 'active') " \
            "RETURN p"
    data = {'query': query.format(src_switch, dst_switch)}
    result_path = requests.post('http://{}:7474/db/data/cypher'.format(neo4j_host), data=data, auth=auth)
    jpath = json.loads(result_path.text)
    if jpath['data']:
        return jpath['data'][0][0]['relationships']
    else:
        return False


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


def get_one_switch_flows(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan, bandwidth, flow_id, cookie):
    forward_output_action = choose_output_action(int(src_vlan), int(dst_vlan))
    reverse_output_action = choose_output_action(int(dst_vlan), int(src_vlan))
    forward_flow = build_one_switch_flow(src_switch, src_port, src_vlan, dst_port, dst_vlan,
                                         bandwidth, flow_id, forward_output_action, forward_cookie(cookie))
    reverse_flow = build_one_switch_flow(dst_switch, dst_port, dst_vlan, src_port, src_vlan,
                                         bandwidth, flow_id, reverse_output_action, reverse_cookie(cookie))
    return [[forward_flow], [reverse_flow]]


def form_flow_links(isls):
    flow_links = []
    for relationship in isls:
        isl = relationship['data']
        flow_links.append("{}-{}".format(str(isl['src_switch']), str(isl['src_port'])))
    return flow_links


def get_path(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan, bandwidth, transit_vlan, flow_id, cookie):
    relationships = get_relationships(src_switch, dst_switch)
    output_action = choose_output_action(int(src_vlan), int(dst_vlan))

    if relationships:
        expanded_relationships = expand_relationships(relationships)
        flows = [build_ingress_flow(expanded_relationships, src_switch, src_port, src_vlan,
                                    bandwidth, transit_vlan, flow_id, output_action, cookie)]
        transit_flow_count = len(expanded_relationships) - 1
        i = 0
        while i < transit_flow_count:
            flows.append(build_intermediate_flows(expanded_relationships, transit_vlan, i, flow_id, cookie))
            i += 1
        flows.append(build_egress_flow(expanded_relationships, dst_switch, dst_port, dst_vlan,
                                       transit_vlan, flow_id, output_action, cookie))
        return flows, expanded_relationships
    else:
        return [], []


def flow_response(flow):
    data = {'last-updated': flow['last_updated'], 'maximum-bandwidth': flow['bandwidth'], 'flowid': flow['flowid'],
            'description': flow['description'], 'cookie': cookie_value(flow['cookie']),
            'source': {'switch-id': flow['src_switch'], 'port-id': flow['src_port'], 'vlan-id': flow['src_vlan']},
            'destination': {'switch-id': flow['dst_switch'], 'port-id': flow['dst_port'], 'vlan-id': flow['dst_vlan']}}
    return data if is_forward_cookie(flow['cookie']) else None


def create_flows(content, transit_vlan_forward, transit_vlan_reverse, cookie):
    source = content['source']
    destination = content['destination']

    if source['switch-id'] == destination['switch-id']:

        all_flows = get_one_switch_flows(
            source['switch-id'], source['port-id'], source['vlan-id'],
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            content['maximum-bandwidth'], content['flowid'], cookie)

        forward_isls = []
        reverse_isls = []
        forward_flow_switches = [str(source['switch-id'])]
        reverse_flow_switches = [str(destination['switch-id'])]

    else:

        forward_flows, forward_isls = get_path(
            source['switch-id'], source['port-id'], source['vlan-id'],
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            content['maximum-bandwidth'], transit_vlan_forward, content['flowid'], forward_cookie(cookie))

        reverse_flows, reverse_isls = get_path(
            destination['switch-id'], destination['port-id'], destination['vlan-id'],
            source['switch-id'], source['port-id'], source['vlan-id'],
            content['maximum-bandwidth'], transit_vlan_reverse, content['flowid'], reverse_cookie(cookie))

        all_flows = [forward_flows, reverse_flows]

        forward_flow_switches = [str(f.switch_id) for f in forward_flows]
        reverse_flow_switches = [str(f.switch_id) for f in reverse_flows]

    return all_flows, forward_flow_switches, reverse_flow_switches,\
           form_flow_links(forward_isls), form_flow_links(reverse_isls)


class Message(object):
    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


def get_timestamp():
    return int(round(time.time() * 1000))


def send_message(payload, correlation_id, message_type):
    message = Message()
    message.payload = payload
    message.type = message_type
    message.destination = "WFM"
    message.timestamp = get_timestamp()
    message.correlation_id = correlation_id
    kafka_message = b'{}'.format(message.to_json())
    print 'topic: {}, message: {}'.format(topic, kafka_message)
    message_result = producer.send(topic, kafka_message)
    message_result.get(timeout=5)


def send_install_commands(all_flows, correlation_id):
    for flows in all_flows:
        for flow in flows:
            send_message(flow, correlation_id, "COMMAND")


def send_remove_commands(switches, flow_id, correlation_id, cookie):
    for switch in switches:
        send_message(delete_flow(switch, str(flow_id), cookie), correlation_id, "COMMAND")


def find_nodes(source, destination):
    start = graph.find_one('switch', property_key='name', property_value='{}'.format(source['switch-id']))
    end = graph.find_one('switch', property_key='name', property_value='{}'.format(destination['switch-id']))
    return start, end


def find_flow_relationships_ids(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} ID(r)"
    flow_relationships_ids = graph.run(query.format(flow_id, "return")).data()
    return flow_relationships_ids


def find_flow_by_id(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} r"
    flow = graph.run(query.format(flow_id, "return")).data()
    return flow


def find_flow_path(flow_id):
    flows = find_flow_by_id(flow_id)

    if is_forward_cookie(flows[0]['r']['cookie']):
        forward_flow = flows[0]['r']
        reverse_flow = flows[1]['r']
    else:
        forward_flow = flows[1]['r']
        reverse_flow = flows[0]['r']

    cookie = cookie_value(forward_flow['cookie'])
    bandwidth = int(forward_flow['bandwidth'])

    return cookie, bandwidth, int(forward_flow['transit_vlan']), int(reverse_flow['transit_vlan']), \
           forward_flow['flowpath'], reverse_flow['flowpath'], forward_flow['isl_path'], reverse_flow['isl_path']


def update_isl_available_bandwidth(links, bandwidth):
    query = "MATCH (a:switch)-[r:isl {{" \
            "src_switch: '{}', " \
            "src_port: '{}'}}]->(b:switch) " \
            "set r.available_bandwidth = r.available_bandwidth - {} return r"

    for link in links:
        isl = re.search('([\w:]+)-(\w+)', link)
        update = query.format(isl.group(1), isl.group(2), bandwidth)
        graph.run(update).data()
        print "isl bandwidth updated: link={}, bandwidth={}".format(link, bandwidth)


def delete_flows_from_database_by_flow_id(flow_id, bandwidth, forward_links, reverse_links):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} r"
    graph.run(query.format(flow_id, "delete")).data()

    update_isl_available_bandwidth(forward_links, (- int(bandwidth)))
    update_isl_available_bandwidth(reverse_links, (- int(bandwidth)))


def delete_flows_from_database_by_relationship_ids(rel_ids, forward_links, reverse_links, bandwidth):
    query = "MATCH (a:switch)-[r:flow]-(b:switch) WHERE id(r)={} {} r"

    for rel_id in rel_ids:
        graph.run(query.format(rel_id['ID(r)'], "delete")).data()

    update_isl_available_bandwidth(forward_links, (- int(bandwidth)))
    update_isl_available_bandwidth(reverse_links, (- int(bandwidth)))


def store_flows(start, end, content, cookie, forward_vlan, reverse_vlan,
                forward_flow_switches, reverse_flow_switches, forward_links, reverse_links):
    bandwidth = content['maximum-bandwidth']
    source = content['source']
    destination = content['destination']
    timestamp = datetime.datetime.utcnow().isoformat()

    query = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) " \
            "MERGE (u)-[:flow {{" \
            "flowid:'{}', " \
            "cookie:'{}', " \
            "bandwidth:'{}', " \
            "src_port: '{}', " \
            "dst_port: '{}', " \
            "src_switch: '{}', " \
            "dst_switch: '{}', " \
            "src_vlan: '{}', " \
            "dst_vlan: '{}'," \
            "transit_vlan: '{}', " \
            "description: '{}', " \
            "last_updated: '{}', " \
            "flowpath: {}, " \
            "isl_path: {}}}]->(r)"

    update_isl_available_bandwidth(forward_links, int(bandwidth))
    update_isl_available_bandwidth(reverse_links, int(bandwidth))

    forward_path = query.format(
        start['name'], end['name'], content['flowid'], forward_cookie(cookie), bandwidth, source['port-id'],
        destination['port-id'], source['switch-id'], destination['switch-id'], source['vlan-id'],
        destination['vlan-id'], forward_vlan, content['description'], timestamp, forward_flow_switches, forward_links)

    reverse_path = query.format(
        end['name'], start['name'], content['flowid'], reverse_cookie(cookie), bandwidth, destination['port-id'],
        source['port-id'], destination['switch-id'], source['switch-id'], destination['vlan-id'],
        source['vlan-id'], reverse_vlan, content['description'], timestamp, reverse_flow_switches, reverse_links)

    graph.run(forward_path)
    graph.run(reverse_path)
