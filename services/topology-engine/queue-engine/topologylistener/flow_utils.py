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
import logging

from topologylistener import db
from topologylistener import message_utils

__all__ = ['graph']


graph = db.create_p2n_driver()
logger = logging.getLogger(__name__)


def choose_output_action(input_vlan_id, output_vlan_id):
    if not int(input_vlan_id):
        return "PUSH" if int(output_vlan_id) else "NONE"
    return "REPLACE" if int(output_vlan_id) else "POP"


def hydrate_flow(db_flow):
    flow = db_flow.copy()
    flow['periodic-pings'] = flow.pop('periodic_pings', False)

    path = json.loads(flow['flowpath'])
    path['clazz'] = 'org.openkilda.messaging.info.event.PathInfoData'
    flow['flowpath'] = path

    return flow


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


def build_commands_to_sync_rules(switch_id, switch_rules):
    """
    Build install commands to sync provided rules with the switch flows.
    """

    installed_rules = set()
    commands = []

    flow_segments = get_flow_segments_by_dst_switch(switch_id)
    for segment in flow_segments:
        cookie = segment['cookie']

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

    return commands, installed_rules


def build_install_command_from_segment(segment):
    """
    Build a command to install required rules for the segment destination.
    """

    # check if the flow is one-switch flow
    if segment['src_switch'] == segment['dst_switch']:
        msg = 'One-switch flow segment {} is provided.'.format(segment)
        logger.error(msg)
        raise ValueError(msg)

    cookie = segment['cookie']
    flow_id = segment['flowid']
    flow = get_flow_by_id_and_cookie(flow_id, cookie)
    if flow is None:
        logger.error("Flow with id %s was not found, cookie %s",
                     flow_id, cookie)
        return

    output_action = choose_output_action(flow['src_vlan'], flow['dst_vlan'])
    switch_id = segment['dst_switch']

    # check if the switch is the destination of the flow
    if switch_id == flow['dst_switch']:
        yield message_utils.build_egress_flow_from_db(flow, output_action, cookie)
    else:
        in_port = segment['dst_port']

        paired_segment = get_flow_segment_by_src_switch_and_cookie(switch_id, cookie)
        if paired_segment is None:
            msg = 'Paired segment for switch {} and cookie {} has not been found.'.format(switch_id, cookie)
            logger.error(msg)
            raise ValueError(msg)

        out_port = paired_segment['src_port']

        yield message_utils.build_intermediate_flows(
            switch_id, in_port, out_port, flow['transit_vlan'],
            flow['flowid'], cookie)


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


def get_flow_segment_by_src_switch_and_cookie(switch_id, cookie):
    query = (
        "MATCH p = (sw:switch)-[fs:flow_segment]->(:switch) "
        "WHERE sw.name=$switch_id AND fs.cookie=$cookie "
        "RETURN fs")
    params = {
        'switch_id': switch_id,
        'cookie': cookie
    }
    db.log_query('Get flow segment by src switch and cookie', query, params)
    result = graph.run(query, params).data()
    if not result:
        return

    segment = result[0]['fs']
    logger.debug('Found segment for switch %s and cookie %s: %s', switch_id, cookie, segment)
    return segment

