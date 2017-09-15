import os
import json
import db

import message_utils
from logger import get_logger


__all__ = ['graph']


neo4j_host = os.environ['neo4jhost']
neo4j_user = os.environ['neo4juser']
neo4j_pass = os.environ['neo4jpass']
graph = db.create_p2n_driver()
auth = (neo4j_user, neo4j_pass)
logger = get_logger()


def is_forward_cookie(cookie):
    return cookie & 0x4000000000000000


def is_reverse_cookie(cookie):
    return cookie & 0x2000000000000000


def is_same_direction(first, second):
    return ((is_forward_cookie(first) and is_forward_cookie(second))
            or (is_reverse_cookie(first) and is_reverse_cookie(second)))


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


def get_one_switch_rules(switch, src_port, src_vlan, dst_port, dst_vlan,
                         bandwidth, flow_id, cookie, meter_id, output_action):
    return [
        message_utils.build_one_switch_flow(
            switch, src_port, src_vlan, dst_port, dst_vlan,
            bandwidth, flow_id, output_action, cookie, meter_id)]


def get_rules(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan,
              bandwidth, transit_vlan, flow_id, cookie, flow_path, meter_id,
              output_action):
    nodes = flow_path.get("path")
    flows = []

    if nodes:
        flows.append(message_utils.build_ingress_flow(
            nodes, src_switch, src_port, src_vlan, bandwidth,
            transit_vlan, flow_id, output_action, cookie, meter_id))

        transit_flow_count = (len(nodes) - 2) / 2
        i = 1
        while i <= transit_flow_count:
            flows.append(message_utils.build_intermediate_flows(
                nodes[i]['switch_id'], nodes[i]['port_no'],
                nodes[i+1]['port_no'], transit_vlan, flow_id, cookie))
            i += 2

        flows.append(message_utils.build_egress_flow(
            nodes, dst_switch, dst_port, dst_vlan,
            transit_vlan, flow_id, output_action, cookie))

    return flows


def build_rules(flow):
    output_action = choose_output_action(
        int(flow['src_vlan']), int(flow['dst_vlan']))

    if flow['src_switch'] == flow['dst_switch']:

        flow_rules = get_one_switch_rules(
            flow['src_switch'],
            int(flow['src_port']),
            int(flow['src_vlan']),
            int(flow['dst_port']),
            int(flow['dst_vlan']),
            int(flow['bandwidth']),
            flow['flowid'],
            int(flow['cookie']),
            int(flow['meter_id']),
            output_action)

    else:

        flow_rules = get_rules(
            flow['src_switch'],
            int(flow['src_port']),
            int(flow['src_vlan']),
            flow['dst_switch'],
            int(flow['dst_port']),
            int(flow['dst_vlan']),
            int(flow['bandwidth']),
            int(flow['transit_vlan']),
            flow['flowid'],
            int(flow['cookie']),
            flow['flowpath'],
            int(flow['meter_id']),
            output_action)

    return flow_rules


def update_path_bandwidth(flow_path, bandwidth):
    query = "MATCH (a:switch)-[r:isl {{" \
            "src_switch: '{}', " \
            "src_port: {}}}]->(b:switch) " \
            "set r.available_bandwidth = r.available_bandwidth - {} return r"

    for node in flow_path.get("path"):
        update = query.format(node['switch_id'], node['port_no'], bandwidth)
        response = graph.run(update).data()

        logger.info('ISL bandwidth update: node=%s, bandwidth=%s, response=%s',
                    node, bandwidth, response)


def remove_flow(flow, flow_path):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) " \
            "WHERE r.cookie = {} DELETE r"
    graph.run(query.format(flow['flowid'], int(flow['cookie']))).data()

    if is_forward_cookie(int(flow['cookie'])):
            update_path_bandwidth(flow_path, -int(flow['bandwidth']))


def store_flow(flow):

    query = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) "\
            "MERGE (u)-[:flow {{" \
            "flowid:'{}', " \
            "cookie: {}, " \
            "bandwidth: {}, " \
            "src_port: {}, " \
            "dst_port: {}, " \
            "src_switch: '{}', " \
            "dst_switch: '{}', " \
            "src_vlan: {}, " \
            "dst_vlan: {}," \
            "transit_vlan: {}, " \
            "description: '{}', " \
            "last_updated: '{}', " \
            "flowpath: '{}'}}]->(r)"

    formatter_query = query.format(
        flow['src_switch'],
        flow['dst_switch'],
        flow['flowid'],
        int(flow['cookie']),
        int(flow['bandwidth']),
        int(flow['src_port']),
        int(flow['dst_port']),
        flow['src_switch'],
        flow['dst_switch'],
        int(flow['src_vlan']),
        int(flow['dst_vlan']),
        int(flow['transit_vlan']),
        flow['description'],
        flow['last_updated'],
        json.dumps(flow['flowpath']))

    graph.run(formatter_query)

    if is_forward_cookie(int(flow['cookie'])):
        update_path_bandwidth(flow['flowpath'], int(flow['bandwidth']))


def get_old_flow(new_flow):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch)" \
            "WHERE r.cookie <> {} RETURN r"
    old_flows = graph.run(query.format(
        new_flow['flowid'], int(new_flow['cookie']))).data()

    if not old_flows:
        message = 'Flow {} not found'.format(new_flow['flowid'])
        logger.error(message)
        raise Exception(message)
    else:
        logger.info('Flows were found: %s', old_flows)

    for data in old_flows:
        old_flow = data['r']
        if is_same_direction(int(new_flow['cookie']), int(old_flow['cookie'])):
            logger.info('Flow was found: flow=%s', old_flow)
            return old_flow


def get_flows():
    flows = {}
    try:
        query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
        result = graph.run(query).data()

        for data in result:
            path = json.loads(data['r']['flowpath'])
            flow = json.loads(json.dumps(data['r'],
                                         default=lambda o: o.__dict__,
                                         sort_keys=True))
            flow['flowpath'] = path
            flow_pair = flows.get(flow['flowid'], {})

            if is_forward_cookie(int(flow['cookie'])):
                flow_pair['forward'] = flow
            else:
                flow_pair['reverse'] = flow
            flows[flow['flowid']] = flow_pair

        logger.info('Got flows: %s', flows.values())

    except Exception as e:
        logger.exception('"Can not get flows: %s', e.message)
        raise

    return flows.values()
