from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response, jsonify
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user
import py2neo
from werkzeug import exceptions as http_errors

import logging
import json
import ConfigParser

from app import application
from . import neo4j_tools

logger = logging.getLogger(__name__)
logger.info ("My Name Is: %s", __name__)

# Adjust the logging level for NEO
logging.getLogger('neo4j.bolt').setLevel(logging.INFO)


config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

graph = neo4j_tools.connect(config)


def is_forward_cookie(cookie):
    cookie = int(cookie)
    # trying to distinguish kilda and not kilda produced cookies
    if cookie & 0xE000000000000000:
        is_match = cookie & 0x4000000000000000
    else:
        is_match = (cookie & 0x0080000000000000) == 0
    return bool(is_match)


def hydrate_flow(one_row):
    """
    :param one_row: The typical result from query - ie  MATCH (a:switch)-[r:flow]->(b:switch) RETURN r
    :return: a fully dict'd object
    """
    path = json.loads(one_row['r']['flowpath'])
    flow = json.loads(json.dumps(one_row['r'],
                                 default=lambda o: o.__dict__,
                                 sort_keys=True))
    path.setdefault('clazz', 'org.openkilda.messaging.info.event.PathInfoData')
    flow['flowpath'] = path
    return flow


def get_flows():
    flows = {}
    query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
    try:
        result = graph.run(query).data()

        for data in result:
            flow = hydrate_flow(data)
            flow['state'] = 'CACHED'
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

switch_states = {
    'active': 'ACTIVATED',
    'inactive': 'DEACTIVATED',
    'removed': 'REMOVED'
}


MT_SWITCH = "org.openkilda.messaging.info.event.SwitchInfoData"
MT_SWITCH_EXTENDED = "org.openkilda.messaging.info.event.SwitchInfoExtendedData"
MT_ISL = "org.openkilda.messaging.info.event.IslInfoData"
MT_PORT = "org.openkilda.messaging.info.event.PortInfoData"
MT_FLOW_INFODATA = "org.openkilda.messaging.info.flow.FlowInfoData"
MT_FLOW_RESPONSE = "org.openkilda.messaging.info.flow.FlowResponse"
MT_SYNC_REQUEST = "org.openkilda.messaging.command.switches.SwitchRulesSyncRequest"
MT_SWITCH_RULES = "org.openkilda.messaging.info.rule.SwitchFlowEntries"
#feature toggle is the functionality to turn off/on specific features
MT_STATE_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleStateRequest"
MT_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleRequest"
MT_NETWORK_TOPOLOGY_CHANGE = (
    "org.openkilda.messaging.info.event.NetworkTopologyChange")
CD_NETWORK = "org.openkilda.messaging.command.discovery.NetworkCommandData"
CD_FLOWS_SYNC_REQUEST = 'org.openkilda.messaging.command.FlowsSyncRequest'
MY_NETWORK_DATA = "org.openkilda.messaging.command.system.FeatureToggleStateRequest"

def get_switches():
    try:
        query = "MATCH (n:switch) RETURN n"
        result = graph.run(query).data()

        switches = []
        for data in result:
            node = data['n']
            switch = {
                'switch_id': node['name'],
                'state': switch_states[node['state']],
                'address': node['address'],
                'hostname': node['hostname'],
                'description': node['description'],
                'controller': node['controller'],
                'clazz': MT_SWITCH,
            }
            switches.append(switch)

        logger.info('Got switches: %s', switches)

    except Exception as e:
        logger.exception('Can not get switches', e.message)
        raise

    return switches


def fetch_isls(pull=True,sort_key='src_switch'):
    """
    :return: an unsorted list of ISL relationships with all properties pulled from the db if pull=True
    """
    try:
        # query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r ORDER BY r.src_switch, r.src_port"
        isls=[]
        rels = graph.match(rel_type="isl")
        for rel in rels:
            if pull:
                graph.pull(rel)
            isls.append(rel)

        if sort_key:
            isls = sorted(isls, key=lambda x: x[sort_key])

        return isls
    except Exception as e:
        logger.exception('FAILED to get ISLs from the DB ', e.message)
        raise


def get_isls():
    try:
        result = fetch_isls()

        isls = []
        for link in result:
            # link = data['r']
            isl = {
                'id': str(
                    link['src_switch'] + '_' + str(link['src_port'])),
                'speed': int(link['speed']),
                'latency_ns': int(link['latency']),
                'available_bandwidth': int(link['available_bandwidth']),
                'state': "DISCOVERED",
                'path': [
                    {'switch_id': str(link['src_switch']),
                     'port_no': int(link['src_port']),
                     'seq_id': 0,
                     'segment_latency': int(link['latency'])},
                    {'switch_id': str(link['dst_switch']),
                     'port_no': int(link['dst_port']),
                     'seq_id': 1,
                     'segment_latency': 0}],
                'clazz': MT_ISL
            }
            isls.append(isl)
        logger.info('Got isls: %s', isls)

    except Exception as e:
        logger.exception('Can not get isls', e.message)
        raise

    return isls


@application.route('/api/v1/dump_network')
@login_required
def dump_network():
    logger.info('Dump network request')

    try:
        step = "switches"
        switches = get_switches()
        logger.debug("%s: %s", step, switches)

        step = "isls"
        isls = get_isls()
        logger.debug("%s: %s", step, isls)

        step = "flows"
        flows = get_flows()
        logger.debug("%s: %s", step, flows)

        return jsonify({
                'switches': switches,
                'isls': isls,
                'flows': flows,
                'clazz': 'org.openkilda.messaging.info.discovery.NetworkInfoData'})

    except Exception as e:
        logger.exception('Can not dump network: %s', e.message)

