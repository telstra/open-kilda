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

import json
import copy
import calendar
import time
import logging

from topologylistener import db
from topologylistener import message_utils


__all__ = ['graph']


graph = db.create_p2n_driver()
raw_logger = logging.getLogger(__name__)


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

    # TODO: Rule creation should migrate closer to path creation .. to do as part of TE / Storm refactor
    # e.g. assuming a refactor of TE into Storm, and possibly more directly attached to the right storm topology
    #       vs a separate topology, then this logic should be closer to path creation
    # TODO: We should leverage the sequence number to ensure we install / remove flows in the right order
    #       e.g. for install, go from the end to beginning; for remove, go in opposite direction.
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


def remove_flow(context, flow, parent_tx=None):
    """
    Deletes the flow and its flow segments. Start with flow segments (symmetrical mirror of store_flow).
    Leverage a parent transaction if it exists, otherwise create / close the transaction within this function.

    - flowid **AND** cookie are *the* primary keys for a flow:
        - both the forward and the reverse flow use the same flowid

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate
    """

    context.log(raw_logger).info('Remove flow: %s', flow['flowid'])
    tx = parent_tx if parent_tx else graph.begin()
    delete_flow_segments(context, flow, tx)
    query = "MATCH (:switch)-[f:flow {{ flowid: '{}', cookie: {} }}]->(:switch) DELETE f".format(flow['flowid'], flow['cookie'])
    result = tx.run(query).data()
    if not parent_tx:
        tx.commit()
    return result


def merge_flow_relationship(flow_data, tx=None):
    """
    This function focuses on just creating the starting/ending switch relationship for a flow.
    """
    query = (
        "MERGE "                                # MERGE .. create if doesn't exist .. being cautious
        " (src:switch {{name:'{src_switch}'}}) "
        " ON CREATE SET src.state = 'inactive' "
        "MERGE "
        " (dst:switch {{name:'{dst_switch}'}}) "
        " ON CREATE SET dst.state = 'inactive' "
        "MERGE (src)-[f:flow {{"                # Should only use the relationship primary keys in a match
        " flowid:'{flowid}', "
        " cookie: {cookie} }} ]->(dst)  "
        "SET "
        " f.meter_id = {meter_id}, "
        " f.bandwidth = {bandwidth}, "
        " f.ignore_bandwidth = {ignore_bandwidth}, "
        " f.src_port = {src_port}, "
        " f.dst_port = {dst_port}, "
        " f.src_switch = '{src_switch}', "
        " f.dst_switch = '{dst_switch}', "
        " f.src_vlan = {src_vlan}, "
        " f.dst_vlan = {dst_vlan}, "
        " f.transit_vlan = {transit_vlan}, "
        " f.description = '{description}', "
        " f.last_updated = '{last_updated}', "
        " f.flowpath = '{flowpath}' "
    )
    flow_data['flowpath'].pop('clazz', None) # don't store the clazz info, if it is there.
    flow_data['last_updated'] = calendar.timegm(time.gmtime())
    flow_data['flowpath'] = json.dumps(flow_data['flowpath'])
    if tx:
        tx.run(query.format(**flow_data))
    else:
        graph.run(query.format(**flow_data))


def merge_flow_segments(context, _flow, tx=None):
    """
    This function creates each segment relationship in a flow, and then it calls the function to
    update bandwidth. This should always be down when creating/merging flow segments.

    To create segments, we leverages the flow path .. and the flow path is a series of nodes, where
    each 2 nodes are the endpoints of an ISL.
    """
    flow = copy.deepcopy(_flow)
    create_segment_query = (
        "MERGE "                                # MERGE .. create if doesn't exist .. being cautious
        "(src:switch {{name:'{src_switch}'}}) "
        "ON CREATE SET src.state = 'inactive' "
        "MERGE "
        "(dst:switch {{name:'{dst_switch}'}}) "
        "ON CREATE SET dst.state = 'inactive' "
        "MERGE "
        "(src)-[fs:flow_segment {{flowid: '{flowid}', parent_cookie: {parent_cookie} }}]->(dst) "
        "SET "
        "fs.cookie = {cookie}, "
        "fs.src_switch = '{src_switch}', "
        "fs.src_port = {src_port}, "
        "fs.dst_switch = '{dst_switch}', "
        "fs.dst_port = {dst_port}, "
        "fs.seq_id = {seq_id}, "
        "fs.segment_latency = {segment_latency}, "
        "fs.bandwidth = {bandwidth}, "
        "fs.ignore_bandwidth = {ignore_bandwidth} "
    )

    flow_path = get_flow_path(flow)
    flow_cookie = flow['cookie']
    flow['parent_cookie'] = flow_cookie  # primary key of parent is flowid & cookie
    context.log(raw_logger).debug(
            'MERGE Flow Segments : %s [path: %s]', flow['flowid'], flow_path)

    for i in range(0, len(flow_path), 2):
        src = flow_path[i]
        dst = flow_path[i+1]
        # <== SRC
        flow['src_switch'] = src['switch_id']
        flow['src_port'] = src['port_no']
        flow['seq_id'] = src['seq_id']
        # Ignore latency if not provided
        flow['segment_latency'] = src.get('segment_latency', 'NULL')
        # ==> DEST
        flow['dst_switch'] = dst['switch_id']
        flow['dst_port'] = dst['port_no']
        # Allow for per segment cookies .. see if it has one set .. otherwise use the cookie of the flow
        # NB: use the "dst cookie" .. since for flow segments, the delete rule will use the dst switch
        flow['cookie'] = dst.get('cookie', flow_cookie)

        # TODO: Preference for transaction around the entire delete
        # TODO: Preference for batch command
        if tx:
            tx.run(create_segment_query.format(**flow))
        else:
            graph.run(create_segment_query.format(**flow))

    update_flow_segment_available_bw(context, flow, tx)


def get_flow_path(flow):
    """
    As commented elsewhere, current algorithm for flow path is to use both endpoints of a segment, each as their own
    node. So, make sure we have an even number of them.
    """
    flow_path = flow['flowpath']['path']
    if len(flow_path) % 2:
        # The current implementation puts 2 nodes per segment .. throw an error if this changes
        raise ValueError(
                'Found un-even number of nodes in the flowpath: '
                '{}'.format(flow_path))
    return flow_path


def delete_flow_segments(context, flow, tx=None):
    """
    Whenever adjusting flow segments, always update available bandwidth. Even when creating a flow
    where we might remove anything old and then create the new .. it isn't guaranteed that the
    old segments are the same as the new segements.. so update bandwidth to be save.
    """
    flow_path = get_flow_path(flow)
    flowid = flow['flowid']
    parent_cookie = flow['cookie']
    context.log(raw_logger).debug(
            'DELETE Flow Segments : flowid: %s parent_cookie: 0x%x [path: %s]',
            flowid, parent_cookie, flow_path)
    delete_segment_query = (
        "MATCH (:switch)-[fs:flow_segment {{ flowid: '{}', parent_cookie: {} }}]->(:switch) DELETE fs"
    )
    if tx:
        tx.run(delete_segment_query.format(flowid, parent_cookie))
    else:
        graph.run(delete_segment_query.format(flowid, parent_cookie))
    update_flow_segment_available_bw(context, flow, tx)


def fetch_flow_segments(flowid, parent_cookie):
    """
    :param flowid: the ID for the entire flow, typically consistent across updates, whereas the cookie may change
    :param parent_cookie: the cookie for the flow as a whole; individual segments may vary
    :return: array of segments
    """
    fetch_query = (
        "MATCH (:switch)-[fs:flow_segment {{ flowid: '{}',parent_cookie: {} }}]->(:switch) RETURN fs ORDER BY fs.seq_id"
    )
    # This query returns type py2neo.types.Relationship .. it has a dict method to return the properties
    result = graph.run(fetch_query.format(flowid, parent_cookie)).data()
    return [dict(x['fs']) for x in result]


def update_flow_segment_available_bw(context, flow, tx=None):
    flow_path = get_flow_path(flow)
    context.log(raw_logger).debug(
            'Update ISL Bandwidth from Flow Segments : %s [path: %s]',
            flow['flowid'], flow_path)
    # TODO: Preference for transaction around the entire delete
    # TODO: Preference for batch command
    for i in range(0, len(flow_path), 2):
        src = flow_path[i]
        dst = flow_path[i+1]
        update_isl_bandwidth(
                context,
                src['switch_id'], src['port_no'],
                dst['switch_id'], dst['port_no'], tx)


def update_isl_bandwidth(
        context, src_switch, src_port, dst_switch, dst_port, tx=None):
    """
    This will update the available_bandwidth for the isl that matches the src/dst information.
    It does this by looking for all flow segments over the ISL, where ignore_bandwidth = false.
    Because there may not be any segments, have to use "OPTIONAL MATCH"
    """
    # print('Update ISL Bandwidth from %s:%d --> %s:%d' % (src_switch, src_port, dst_switch, dst_port))

    available_bw_query = (
        "MATCH (src:switch {{name:'{src_switch}'}}), (dst:switch {{name:'{dst_switch}'}}) WITH src,dst "
        " MATCH (src)-[i:isl {{ src_port:{src_port}, dst_port: {dst_port}}}]->(dst) WITH src,dst,i "
        " OPTIONAL MATCH (src)-[fs:flow_segment {{ src_port:{src_port}, dst_port: {dst_port}, ignore_bandwidth: false }}]->(dst) "
        " WITH sum(fs.bandwidth) AS used_bandwidth, i as i "
        " SET i.available_bandwidth = i.max_bandwidth - used_bandwidth "
    )

    context.log(raw_logger).debug(
            'Update ISL Bandwidth from %s:%d --> %s:%d',
            src_switch, src_port, dst_switch, dst_port)
    params = {
        'src_switch': src_switch,
        'src_port': src_port,
        'dst_switch': dst_switch,
        'dst_port': dst_port,
    }
    query = available_bw_query.format(**params)
    if tx:
        tx.run(query)
    else:
        graph.run(query)


def store_flow(context, flow, tx=None):
    """
    Create a :flow relationship between the starting and ending switch, as well as
    create :flow_segment relationships between every switch in the path.

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate

    :param flow:
    :param tx: The transaction to use, or no transaction.
    :return:
    """
    # TODO: Preference for transaction around the entire set of store operations

    context.log(raw_logger).debug('STORE Flow : %s', flow['flowid'])
    delete_flow_segments(context, flow, tx)
    merge_flow_relationship(copy.deepcopy(flow), tx)
    merge_flow_segments(context, flow, tx)


def hydrate_flow(one_row):
    """
    :param one_row: The typical result from query - ie  MATCH (a:switch)-[r:flow]->(b:switch) RETURN r
    :return: a fully dict'd object
    """
    path = json.loads(one_row['r']['flowpath'])
    flow = json.loads(json.dumps(one_row['r'],
                                 default=lambda o: o.__dict__,
                                 sort_keys=True))
    flow['flowpath'] = path
    return flow


def get_old_flow(context, new_flow):
    log = context.log(raw_logger)

    query = (
        "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) " 
        " WHERE r.cookie <> {} RETURN r "
    )
    old_flows = graph.run(query.format(
        new_flow['flowid'], int(new_flow['cookie']))).data()

    if not old_flows:
        message = 'Flow {} not found'.format(new_flow['flowid'])
        log.error('%s', message)
        # TODO (aovchinnikov): replace with specific exception.
        raise Exception(message)

    log.info('Flows were found: %s', old_flows)
    for data in old_flows:
        old_flow = hydrate_flow(data)
        log.info('check cookies: %s ? %s',
                 new_flow['cookie'], old_flow['cookie'])
        if is_same_direction(new_flow['cookie'], old_flow['cookie']):
            log.info('Flow was found: flow=%s', old_flow)
            return dict(old_flow)

    # FIXME(surabujin): can return None!


def get_flows(context):
    flows = {}
    query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
    result = graph.run(query).data()

    for data in result:
        flow = hydrate_flow(data)
        flow_pair = flows.get(flow['flowid'], {})
        if is_forward_cookie(flow['cookie']):
            flow_pair['forward'] = flow
        else:
            flow_pair['reverse'] = flow
        flows[flow['flowid']] = flow_pair

    context.log(raw_logger).info('Got flows: %s', flows.values())
    return flows.values()
