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

import copy
import json
import logging
import time

from topologylistener import db
from topologylistener import message_utils
from topologylistener import model

__all__ = ['graph']


graph = db.create_p2n_driver()
logger = logging.getLogger(__name__)

default_rules = ['0x8000000000000001', '0x8000000000000002',
                 '0x8000000000000003', '0x8000000000000004']


cookie_flag_forward = 0x4000000000000000
cookie_flag_reverse = 0x2000000000000000


def is_forward_cookie(cookie):
    cookie = int(cookie)
    # trying to distinguish kilda and not kilda produced cookies
    if cookie & 0xE000000000000000:
        is_match = cookie & cookie_flag_forward
    else:
        is_match = (cookie & 0x0080000000000000) == 0
    return bool(is_match)


def is_reverse_cookie(cookie):
    cookie = int(cookie)
    # trying to distinguish kilda and not kilda produced cookies
    if cookie & 0xE000000000000000:
        is_match = cookie & cookie_flag_reverse
    else:
        is_match = (cookie & 0x0080000000000000) != 0
    return bool(is_match)


def cookie_to_hex(cookie):
    value = hex(
        ((cookie ^ 0xffffffffffffffff) + 1) * -1 if cookie < 0 else cookie)
    if value.endswith("L"):
        value = value[:-1]
    return value


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

    for i in range(1, len(nodes)-1, 2):
        src = nodes[i]
        dst = nodes[i+1]

        if src['switch_id'] != dst['switch_id']:
            msg = 'Found non-paired node in the flowpath: {}'.format(flowpath)
            logger.error(msg)
            raise ValueError(msg)

        segment_cookie = src.get('cookie', cookie)

        flows.append(message_utils.build_intermediate_flows(
            src['switch_id'], src['port_no'], dst['port_no'], transit_vlan, flowid,
            segment_cookie))

    # Egress flow has cookie of the last segment.
    egress_flow_cookie = cookie
    if nodes:
        egress_flow_cookie = nodes[-1].get('cookie', cookie)

    flows.append(message_utils.build_egress_flow(
        nodes, dst_switch, dst_port, dst_vlan,
        transit_vlan, flowid, output_action, egress_flow_cookie))

    return flows


def build_rules(flow):
    output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
    if flow['src_switch'] == flow['dst_switch']:
        return get_one_switch_rules(output_action=output_action, **flow)
    return get_rules(output_action=output_action, **flow)


def remove_flow(flow, parent_tx=None):
    """
    Deletes the flow and its flow segments. Start with flow segments (symmetrical mirror of store_flow).
    Leverage a parent transaction if it exists, otherwise create / close the transaction within this function.

    - flowid **AND** cookie are *the* primary keys for a flow:
        - both the forward and the reverse flow use the same flowid

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate
    """

    logger.info('Remove flow: %s', flow['flowid'])
    tx = parent_tx if parent_tx else graph.begin()
    delete_flow_segments(flow, tx)
    query = (
        "MATCH (:switch) - [f:flow {"
        " flowid: $flowid, "
        " cookie: $cookie "
        "}] -> (:switch) "
        "DELETE f")
    params = {
        'flowid': flow['flowid'],
        'cookie': flow['cookie']
    }
    db.log_query('Remove flow', query, params)
    tx.run(query, params).data()
    if not parent_tx:
        tx.commit()


def merge_flow_relationship(flow, tx):
    q = (
        "MERGE (src:switch {name: $src_switch}) "
        " ON CREATE SET src.state = 'inactive' "
        "MERGE (dst:switch {name: $dst_switch}) "
        " ON CREATE SET dst.state = 'inactive' "
        "MERGE (src)-[f:flow {"
        " flowid: $flowid, "
        " cookie: $cookie } ]->(dst)"
        "SET f.src_switch = src.name, "
        " f.src_port = $src_port, "
        " f.src_vlan = $src_vlan, "
        " f.dst_switch = dst.name, "
        " f.dst_port = $dst_port, "
        " f.dst_vlan = $dst_vlan, "
        " f.meter_id = $meter_id, "
        " f.bandwidth = $bandwidth, "
        " f.ignore_bandwidth = $ignore_bandwidth, "
        " f.periodic_pings = $periodic_pings, "
        " f.transit_vlan = $transit_vlan, "
        " f.description = $description, "
        " f.last_updated = $last_updated, "
        " f.flowpath = $flowpath"
    )

    p = model.dash_to_underscore(flow)
    # FIXME(surabujin): do we really want to keep this time representation?
    # FIXME(surabujin): format datetime as '1532609693'(don 't match with
    #                   format used in PCE/resource cache)
    p['last_updated'] = str(int(time.time()))

    path = p['flowpath'].copy()
    path.pop('clazz', None)
    p['flowpath'] = json.dumps(path)

    db.log_query('Save(update) flow', q, p)
    tx.run(q, p)


def merge_flow_segments(_flow, tx=None):
    """
    This function creates each segment relationship in a flow, and then it calls the function to
    update bandwidth. This should always be down when creating/merging flow segments.

    To create segments, we leverages the flow path .. and the flow path is a series of nodes, where
    each 2 nodes are the endpoints of an ISL.
    """
    flow = copy.deepcopy(_flow)
    create_segment_query = (
        "MERGE "
        " (src:switch {name: $src_switch}) "
        "ON CREATE SET src.state='inactive' "
        "MERGE "
        " (dst:switch {name: $dst_switch}) "
        "ON CREATE SET dst.state='inactive' "
        "MERGE (src) - [fs:flow_segment {"
        " flowid: $flowid, "
        " parent_cookie: $parent_cookie "
        "}] -> (dst) "
        "SET "
        " fs.cookie=$cookie, "
        " fs.src_switch=$src_switch, "
        " fs.src_port=$src_port, "
        " fs.dst_switch=$dst_switch, "
        " fs.dst_port=$dst_port, "
        " fs.seq_id=$seq_id, "
        " fs.segment_latency=$segment_latency, "
        " fs.bandwidth=$bandwidth, "
        " fs.ignore_bandwidth=$ignore_bandwidth ")

    flow_path = get_flow_path(flow)
    flow_cookie = flow['cookie']
    flow['parent_cookie'] = flow_cookie  # primary key of parent is flowid & cookie
    logger.debug('MERGE Flow Segments : %s [path: %s]', flow['flowid'], flow_path)

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

        db.log_query('Merge flow segment', create_segment_query, flow)
        # TODO: Preference for transaction around the entire delete
        # TODO: Preference for batch command
        if tx:
            tx.run(create_segment_query, flow)
        else:
            graph.run(create_segment_query, flow)

    update_flow_segment_available_bw(flow, tx)


def get_flow_path(flow):
    """
    As commented elsewhere, current algorithm for flow path is to use both endpoints of a segment, each as their own
    node. So, make sure we have an even number of them.
    """
    flow_path = flow['flowpath']['path']
    if len(flow_path) % 2 != 0:
        # The current implementation puts 2 nodes per segment .. throw an error if this changes
        msg = 'Found un-even number of nodes in the flowpath: {}'.format(flow_path)
        logger.error(msg)
        raise ValueError(msg)
    return flow_path


def delete_flow_segments(flow, tx=None):
    """
    Whenever adjusting flow segments, always update available bandwidth. Even when creating a flow
    where we might remove anything old and then create the new .. it isn't guaranteed that the
    old segments are the same as the new segements.. so update bandwidth to be save.
    """
    flow_path = get_flow_path(flow)
    flowid = flow['flowid']
    parent_cookie = flow['cookie']
    logger.debug('DELETE Flow Segments : flowid: %s parent_cookie: 0x%x [path: %s]', flowid, parent_cookie, flow_path)
    delete_segment_query = (
        "MATCH (:switch) - [fs:flow_segment { "
        " flowid: $flowid, "
        " parent_cookie: $parent_cookie "
        "}] -> (:switch) "
        "DELETE fs")
    params = {
        'flowid': flowid,
        'parent_cookie': parent_cookie
    }
    db.log_query('Delete flow segments', delete_segment_query, params)
    if tx:
        tx.run(delete_segment_query, params)
    else:
        graph.run(delete_segment_query, params)
    update_flow_segment_available_bw(flow, tx)


def fetch_flow_segments(flowid, parent_cookie):
    """
    :param flowid: the ID for the entire flow, typically consistent across updates, whereas the cookie may change
    :param parent_cookie: the cookie for the flow as a whole; individual segments may vary
    :return: array of segments
    """
    fetch_query = (
        "MATCH (:switch) - [fs:flow_segment { "
        " flowid: $flowid,"
        " parent_cookie: $parent_cookie "
        "}] -> (:switch) "
        "RETURN fs "
        "ORDER BY fs.seq_id")
    params = {
        'flowid': flowid,
        'parent_cookie': parent_cookie
    }
    db.log_query('Fetch flow segments', fetch_query, params)
    # This query returns type py2neo.types.Relationship .. it has a dict method to return the properties
    result = graph.run(fetch_query, params).data()
    return [dict(x['fs']) for x in result]


def update_flow_segment_available_bw(flow, tx=None):
    flow_path = get_flow_path(flow)
    logger.debug('Update ISL Bandwidth from Flow Segments : %s [path: %s]', flow['flowid'], flow_path)
    # TODO: Preference for transaction around the entire delete
    # TODO: Preference for batch command
    for i in range(0, len(flow_path), 2):
        src = flow_path[i]
        dst = flow_path[i+1]
        update_isl_bandwidth(src['switch_id'], src['port_no'], dst['switch_id'], dst['port_no'], tx)


def update_isl_bandwidth(src_switch, src_port, dst_switch, dst_port, tx=None):
    """
    This will update the available_bandwidth for the isl that matches the src/dst information.
    It does this by looking for all flow segments over the ISL, where ignore_bandwidth = false.
    Because there may not be any segments, have to use "OPTIONAL MATCH"
    """
    # print('Update ISL Bandwidth from %s:%d --> %s:%d' % (src_switch, src_port, dst_switch, dst_port))

    available_bw_query = (
        "MATCH "
        " (src:switch {name: $src_switch}), "
        " (dst:switch {name: $dst_switch}) "
        "WITH src,dst "
        "MATCH (src) - [i:isl { "
        " src_port: $src_port, "
        " dst_port: $dst_port "
        "}] -> (dst) "
        "WITH src,dst,i "
        "OPTIONAL MATCH (src) - [fs:flow_segment { "
        " src_port: $src_port, "
        " dst_port: $dst_port, "
        " ignore_bandwidth: false "
        "}] -> (dst) "
        "WITH sum(fs.bandwidth) AS used_bandwidth, i as i "
        "SET i.available_bandwidth = i.max_bandwidth - used_bandwidth ")

    logger.debug('Update ISL Bandwidth from %s:%d --> %s:%d' % (src_switch, src_port, dst_switch, dst_port))
    params = {
        'src_switch': src_switch,
        'src_port': src_port,
        'dst_switch': dst_switch,
        'dst_port': dst_port,
    }
    db.log_query('Update ISL bandwidth', available_bw_query, params)
    if tx:
        tx.run(available_bw_query, params)
    else:
        graph.run(available_bw_query, params)


def store_flow(flow, tx):
    """
    Create a :flow relationship between the starting and ending switch, as well as
    create :flow_segment relationships between every switch in the path.

    NB: store_flow is used for uni-direction .. whereas flow_id is used both directions .. need cookie to differentiate

    :param flow:
    :param tx: The transaction to use, or no transaction.
    :return:
    """
    # TODO: Preference for transaction around the entire set of store operations

    logger.debug('STORE Flow : %s', flow['flowid'])
    delete_flow_segments(flow, tx)
    merge_flow_relationship(flow, tx)
    merge_flow_segments(flow, tx)


def hydrate_flow(db_flow):
    flow = db_flow.copy()
    flow['periodic-pings'] = flow.pop('periodic_pings', False)

    path = json.loads(flow['flowpath'])
    path['clazz'] = 'org.openkilda.messaging.info.event.PathInfoData'
    flow['flowpath'] = path

    return flow


def get_old_flow(new_flow):
    query = (
        "MATCH (a:switch) - [r:flow {"
        " flowid: $flowid "
        "}] -> (b:switch) "
        "WHERE r.cookie <> $cookie "
        "RETURN r ")
    params = {
        'flowid': new_flow['flowid'],
        'cookie': int(new_flow['cookie'])
    }
    db.log_query('Get old flow', query, params)
    old_flows = graph.run(query, params).data()

    if not old_flows:
        message = 'Flow {} not found'.format(new_flow['flowid'])
        logger.error(message)
        # TODO (aovchinnikov): replace with specific exception.
        raise Exception(message)
    else:
        logger.info('Flows were found: %s', old_flows)

    for data in old_flows:
        old_flow = hydrate_flow(data['r'])
        logger.info('check cookies: %s ? %s',
                    new_flow['cookie'], old_flow['cookie'])
        if is_same_direction(new_flow['cookie'], old_flow['cookie']):
            logger.info('Flow was found: flow=%s', old_flow)
            return dict(old_flow)

    # FIXME(surabujin): use custom exception!!!
    raise Exception(
        'Requested flow {}(cookie={}) don\'t found corresponding flow (with '
        'matching direction in Neo4j)'.format(
            new_flow['flowid'], new_flow['cookie']))


def precreate_switches(tx, *nodes):
    switches = [x.lower() for x in nodes]
    switches.sort()

    for dpid in switches:
        query = (
            "MERGE (sw:switch {name: $dpid}) "
            "ON CREATE SET sw.state='inactive' "
            "ON MATCH SET sw.tx_override_workaround='dummy'")
        params = {
            'dpid': dpid
        }
        db.log_query('Precreate switches', query, params)
        tx.run(query, params)


def get_flow_segments_by_dst_switch(switch_id):
    query = (
        "MATCH p = (:switch)-[fs:flow_segment]->(sw:switch) "
        "WHERE sw.name=$switch_id "
        "RETURN fs")
    params = {
        'switch_id': switch_id
    }
    db.log_query('Get flow segments by dst switch', query, params)
    result = graph.run(query, params).data()

    # group flow_segments by parent cookie, it is helpful for building
    # transit switch rules
    segments = []
    for relationship in result:
        segments.append(relationship['fs'])

    logger.debug('Found segments for switch %s: %s', switch_id, segments)

    return segments


def get_flows_by_src_switch(switch_id):
    query = (
        "MATCH (sw:switch)-[r:flow]->(:switch) "
        "WHERE sw.name=$switch_id RETURN r")
    params = {
        'switch_id': switch_id
    }
    db.log_query('Get flows by src switch', query, params)
    result = graph.run(query, params).data()

    flows = []
    for item in result:
        flows.append(hydrate_flow(item['r']))

    logger.debug('Found flows for switch %s: %s', switch_id, flows)

    return flows


def validate_switch_rules(switch_id, switch_rules):
    """
    Perform validation of provided rules against the switch flows.
    """

    switch_cookies = [x['cookie'] for x in switch_rules]

    # define three types of result rules
    missing_rules = set()
    excess_rules = set()
    proper_rules = set()

    # check whether the switch has segments' cookies
    flow_segments = get_flow_segments_by_dst_switch(switch_id)
    for segment in flow_segments:
        cookie = segment.get('cookie', segment['parent_cookie'])

        if cookie not in switch_cookies:
            logger.warn('Rule %s is not found on switch %s', cookie, switch_id)
            missing_rules.add(cookie)
        else:
            proper_rules.add(cookie)

    # check whether the switch has ingress flows (as well as one-switch flows).
    ingress_flows = get_flows_by_src_switch(switch_id)
    for flow in ingress_flows:
        cookie = flow['cookie']

        if cookie not in switch_cookies:
            logger.warn("Ingress or one-switch flow %s is missing on switch %s", cookie, switch_id)
            missing_rules.add(cookie)
        else:
            proper_rules.add(cookie)

    # check whether the switch has redundant rules, skipping the default rules.
    for cookie in switch_cookies:
        if cookie not in proper_rules and cookie_to_hex(cookie) not in default_rules:
            logger.warn('Rule %s is excessive on the switch %s', cookie, switch_id)
            excess_rules.add(cookie)

    level = logging.INFO
    if missing_rules:
        level = logging.ERROR
    elif excess_rules:
        level = logging.WARN

    diff = {
        "missing_rules": missing_rules,
        "excess_rules": excess_rules,
        "proper_rules": proper_rules}

    logger.log(level, 'Switch %s rules validation result: %s', switch_id, diff)

    return diff


def build_commands_to_sync_rules(switch_id, switch_rules):
    """
    Build install commands to sync provided rules with the switch flows.
    """

    installed_rules = set()
    commands = []

    flow_segments = get_flow_segments_by_dst_switch(switch_id)
    for segment in flow_segments:
        cookie = segment.get('cookie', segment['parent_cookie'])

        if cookie in switch_rules:
            logger.info('Rule %s is to be (re)installed on switch %s', cookie, switch_id)
            installed_rules.add(cookie)
            commands.extend(build_install_command_from_segment(segment))

    ingress_flows = get_flows_by_src_switch(switch_id)
    for flow in ingress_flows:
        cookie = flow['cookie']

        if cookie in switch_rules:
            installed_rules.add(cookie)

            output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])

            # check if the flow is one-switch flow
            if flow['src_switch'] == flow['dst_switch']:
                logger.info("One-switch flow %s is to be (re)installed on switch %s", cookie, switch_id)
                commands.append(message_utils.build_one_switch_flow_from_db(switch_id, flow, output_action))
            else:
                logger.info("Ingress flow %s is to be (re)installed on switch %s", cookie, switch_id)
                commands.append(message_utils.build_ingress_flow_from_db(flow, output_action))

    return {"commands": commands, "installed_rules": installed_rules}


def build_install_command_from_segment(segment):
    """
    Build a command to install required rules for the segment destination.
    """

    # check if the flow is one-switch flow
    if segment['src_switch'] == segment['dst_switch']:
        msg = 'One-switch flow segment {} is provided.'.format(segment)
        logger.error(msg)
        raise ValueError(msg)

    parent_cookie = segment['parent_cookie']
    flow_id = segment['flowid']
    flow = get_flow_by_id_and_cookie(flow_id, parent_cookie)
    if flow is None:
        logger.error("Flow with id %s was not found, cookie %s",
                     flow_id, parent_cookie)
        return

    output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
    switch_id = segment['dst_switch']
    segment_cookie = segment['cookie']

    # check if the switch is the destination of the flow
    if switch_id == flow['dst_switch']:
        yield message_utils.build_egress_flow_from_db(flow, output_action, segment_cookie)
    else:
        in_port = segment['dst_port']

        paired_segment = get_flow_segment_by_src_switch_and_cookie(switch_id, parent_cookie)
        if paired_segment is None:
            msg = 'Paired segment for switch {} and cookie {} has not been found.'.format(switch_id, parent_cookie)
            logger.error(msg)
            raise ValueError(msg)

        out_port = paired_segment['src_port']

        yield message_utils.build_intermediate_flows(
            switch_id, in_port, out_port, flow['transit_vlan'],
            flow['flowid'], segment_cookie)


def get_flow_by_id_and_cookie(flow_id, cookie):
    query = (
        "MATCH ()-[r:flow]->() WHERE r.flowid=$flow_id "
        "and r.cookie=$cookie RETURN r")
    params = {
        'flow_id': flow_id,
        'cookie': cookie
    }
    db.log_query('Get flow by id and cookie', query, params)
    result = graph.run(query, params).data()
    if not result:
        return

    flow = hydrate_flow(result[0]['r'])
    logger.debug('Found flow for id %s and cookie %s: %s', flow_id, cookie, flow)
    return flow


def get_flow_segment_by_src_switch_and_cookie(switch_id, parent_cookie):
    query = (
        "MATCH p = (sw:switch)-[fs:flow_segment]->(:switch) "
        "WHERE sw.name=$switch_id AND fs.parent_cookie=$parent_cookie "
        "RETURN fs")
    params = {
        'switch_id': switch_id,
        'parent_cookie': parent_cookie
    }
    db.log_query('Get flow segment by src switch and cookie', query, params)
    result = graph.run(query, params).data()
    if not result:
        return

    segment = result[0]['fs']
    logger.debug('Found segment for switch %s and parent_cookie %s: %s', switch_id, parent_cookie, segment)
    return segment

