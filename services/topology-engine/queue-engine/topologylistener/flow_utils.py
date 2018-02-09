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

import os
import json
import db
import copy

import message_utils
import logging


__all__ = ['graph']


graph = db.create_p2n_driver()
logger = logging.getLogger(__name__)


def is_forward_cookie(cookie):
    return int(cookie) & 0x4000000000000000


def is_reverse_cookie(cookie):
    return int(cookie) & 0x2000000000000000


def is_same_direction(first, second):
    return ((is_forward_cookie(first) and is_forward_cookie(second))
            or (is_reverse_cookie(first) and is_reverse_cookie(second)))


def choose_output_action(input_vlan_id, output_vlan_id):
    if not int(input_vlan_id):
        return "PUSH" if int(output_vlan_id) else "NONE"
    return "REPLACE" if int(output_vlan_id) else "POP"


def get_one_switch_rules(src_switch, src_port, src_vlan, dst_port, dst_vlan,
                         bandwidth, flowid, cookie, meter_id, output_action,
                         **k):
    return [
        message_utils.build_one_switch_flow(
            src_switch, src_port, src_vlan, dst_port, dst_vlan,
            bandwidth, flowid, output_action, cookie, meter_id)]


def get_rules(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan,
              bandwidth, transit_vlan, flowid, cookie, flowpath, meter_id,
              output_action, **k):
    nodes = flowpath.get("path")
    if not nodes:
        return []

    flows = []

    flows.append(message_utils.build_ingress_flow(
        nodes, src_switch, src_port, src_vlan, bandwidth,
        transit_vlan, flowid, output_action, cookie, meter_id))

    flows.extend(message_utils.build_intermediate_flows(
        _['switch_id'], _['port_no'], __['port_no'], transit_vlan, flowid,
        cookie) for _, __ in zip(nodes[1:-1], nodes[2:]))

    flows.append(message_utils.build_egress_flow(
        nodes, dst_switch, dst_port, dst_vlan,
        transit_vlan, flowid, output_action, cookie))

    return flows


def build_rules(flow):
    output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
    if flow['src_switch'] == flow['dst_switch']:
        return get_one_switch_rules(output_action=output_action, **flow)
    return get_rules(output_action=output_action, **flow)


def update_path_bandwidth(nodes, bandwidth):
    query = "MATCH (a:switch)-[r:isl {{" \
            "src_switch: '{}', " \
            "src_port: {}}}]->(b:switch) " \
            "set r.available_bandwidth = r.available_bandwidth - {} return r"

    for node in nodes:
        update = query.format(node['switch_id'], node['port_no'], bandwidth)
        response = graph.run(update).data()
        logger.info('ISL bandwidth update: node=%s, bandwidth=%s, response=%s',
                    node, bandwidth, response)


def remove_flow(flow, flow_path):
    logger.info('Remove flow: %s', flow['flowid'])

    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) " \
            "WHERE r.cookie = {} DELETE r"
    graph.run(query.format(flow['flowid'], int(flow['cookie']))).data()

    if not flow['ignore-bandwidth'] and is_forward_cookie(flow['cookie']):
            update_path_bandwidth(flow_path, -int(flow['bandwidth']))


def store_flow(flow):

    flow_data = copy.deepcopy(flow)

    query = ("MATCH (u:switch {{name:'{src_switch}'}}), "
             "(r:switch {{name:'{dst_switch}'}}) "
             "MERGE (u)-[:flow {{"
             "flowid:'{flowid}', "
             "cookie: {cookie}, "
             "meter_id: {meter_id}, "
             "bandwidth: {bandwidth}, "
             "src_port: {src_port}, "
             "dst_port: {dst_port}, "
             "src_switch: '{src_switch}', "
             "dst_switch: '{dst_switch}', "
             "src_vlan: {src_vlan}, "
             "dst_vlan: {dst_vlan},"
             "transit_vlan: {transit_vlan}, "
             "description: '{description}', "
             "last_updated: '{last_updated}', "
             "flowpath: '{flowpath}'}}]->(r)")
    path = flow_data['flowpath']['path']
    flow_data['flowpath'] = json.dumps(flow_data['flowpath'])
    graph.run(query.format(**flow_data))

    # the path is bidirectional .. only deduct bandwidth once (in forward direction)
    if not flow['ignore-bandwidth'] and is_forward_cookie(flow_data['cookie']):
        update_path_bandwidth(path, int(flow_data['bandwidth']))


def get_old_flow(new_flow):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch)" \
            "WHERE r.cookie <> {} RETURN r"
    old_flows = graph.run(query.format(
        new_flow['flowid'], int(new_flow['cookie']))).data()

    if not old_flows:
        message = 'Flow {} not found'.format(new_flow['flowid'])
        logger.error(message)
        # TODO (aovchinnikov): replace with specific exception.
        raise Exception(message)
    else:
        logger.info('Flows were found: %s', old_flows)

    for data in old_flows:
        old_flow = data['r']
        logger.info('check cookies: %s ? %s',
                    new_flow['cookie'], old_flow['cookie'])
        if is_same_direction(new_flow['cookie'], old_flow['cookie']):
            logger.info('Flow was found: flow=%s', old_flow)
            return old_flow


def get_flows():
    flows = {}
    query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
    try:
        result = graph.run(query).data()

        for data in result:
            path = json.loads(data['r']['flowpath'])
            flow = json.loads(json.dumps(data['r'],
                                         default=lambda o: o.__dict__,
                                         sort_keys=True))
            flow['flowpath'] = path
            flow_pair = flows.get(flow['flowid'], {})
            if is_forward_cookie(flow['cookie']):
                flow_pair['forward'] = flow
            else:
                flow_pair['reverse'] = flow
            flows[flow['flowid']] = flow_pair
        logger.info('Got flows: %s', flows.values())
    except Exception as e:
        logger.exception('"Can not get flows: %s', e.message)
        raise
    return flows.values()
