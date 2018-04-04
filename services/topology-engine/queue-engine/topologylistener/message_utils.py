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

import collections
import copy
import time
import json
import logging

from kafka import KafkaProducer
import config

producer = KafkaProducer(bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS)
logger = logging.getLogger(__name__)

MT_ERROR = "org.openkilda.messaging.error.ErrorMessage"
MT_COMMAND = "org.openkilda.messaging.command.CommandMessage"
MT_COMMAND_REPLY = "org.openkilda.messaging.command.CommandWithReplyToMessage"
MT_INFO = "org.openkilda.messaging.info.InfoMessage"
MT_INFO_FLOW_STATUS = "org.openkilda.messaging.info.flow.FlowStatusResponse"
MT_ERROR_DATA = "org.openkilda.messaging.error.ErrorData"

MT_NETWORK = "org.openkilda.messaging.info.discovery.NetworkInfoData"

Kb = 2 ** 10


def get_timestamp():
    return int(round(time.time() * 1000))


class Flow(object):
    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__)


def build_ingress_flow(path_nodes, src_switch, src_port, src_vlan,
                       bandwidth, transit_vlan, flow_id, output_action,
                       cookie, meter_id):
    output_port = None

    for path_node in path_nodes:
        if path_node['switch_id'] == src_switch:
            output_port = int(path_node['port_no'])

    if not output_port:
        raise ValueError('Output port was not found for ingress flow rule',
                         "path={}".format(path_nodes))

    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.InstallIngressFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = src_switch
    flow.input_port = src_port
    flow.output_port = output_port
    flow.input_vlan_id = src_vlan
    flow.transit_vlan_id = transit_vlan
    flow.output_vlan_type = output_action
    flow.bandwidth = bandwidth
    flow.meter_id = meter_id

    return flow


def build_ingress_flow_from_db(stored_flow, output_action):
    return build_ingress_flow(stored_flow['flowpath']['path'],
                              stored_flow['src_switch'],
                              stored_flow['src_port'], stored_flow['src_vlan'],
                              stored_flow['bandwidth'],
                              stored_flow['transit_vlan'],
                              stored_flow['flowid'], output_action,
                              stored_flow['cookie'], stored_flow['meter_id'])


def build_egress_flow(path_nodes, dst_switch, dst_port, dst_vlan,
                      transit_vlan, flow_id, output_action, cookie):
    input_port = None

    for path_node in path_nodes:
        if path_node['switch_id'] == dst_switch:
            input_port = int(path_node['port_no'])

    if not input_port:
        raise ValueError('Input port was not found for egress flow rule',
                         "path={}".format(path_nodes))

    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.InstallEgressFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = dst_switch
    flow.input_port = input_port
    flow.output_port = dst_port
    flow.transit_vlan_id = transit_vlan
    flow.output_vlan_id = dst_vlan
    flow.output_vlan_type = output_action

    return flow


def build_egress_flow_from_db(stored_flow, output_action):
    print stored_flow
    return build_egress_flow(stored_flow['flowpath']['path'],
                             stored_flow['dst_switch'], stored_flow['dst_port'],
                             stored_flow['dst_vlan'],
                             stored_flow['transit_vlan'],
                             stored_flow['flowid'], output_action,
                             stored_flow['cookie'])


def build_intermediate_flows(switch, match, action, vlan, flow_id, cookie):
    # output action is always NONE for transit vlan id

    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.InstallTransitFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = switch
    flow.input_port = match
    flow.output_port = action
    flow.transit_vlan_id = vlan

    return flow

# TODO: A number of todos around why we have a special code parth for one switch flows
def build_one_switch_flow(switch, src_port, src_vlan, dst_port, dst_vlan,
                          bandwidth, flow_id, output_action, cookie,
                          meter_id):
    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.InstallOneSwitchFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = switch
    flow.input_port = src_port
    flow.output_port = dst_port
    flow.input_vlan_id = src_vlan
    flow.output_vlan_id = dst_vlan
    flow.output_vlan_type = output_action
    flow.bandwidth = bandwidth
    flow.meter_id = meter_id

    return flow


def build_one_switch_flow_from_db(switch, stored_flow, output_action):
    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.InstallOneSwitchFlow"
    flow.transaction_id = 0
    flow.flowid = stored_flow['flowid']
    flow.cookie = stored_flow['cookie']
    flow.switch_id = switch
    flow.input_port = stored_flow['src_port']
    flow.output_port = stored_flow['dst_port']
    flow.input_vlan_id = stored_flow['src_vlan']
    flow.output_vlan_id = stored_flow['dst_vlan']
    flow.output_vlan_type = output_action
    flow.bandwidth = stored_flow['bandwidth']
    flow.meter_id = stored_flow['meter_id']

    return flow


def build_delete_flow(switch, flow_id, cookie, meter_id=0):
    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.RemoveFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = switch
    flow.meter_id = meter_id

    return flow


def make_features_status_response():
    message = Message()
    message.clazz = "org.openkilda.messaging.info.system.FeatureTogglesResponse"

    return message


def send_dump_rules_request(switch_id, correlation_id):
    message = Message()
    message.clazz = 'org.openkilda.messaging.command.switches.DumpRulesRequest'
    message.switch_id = switch_id
    reply_to = {"reply_to": config.KAFKA_TOPO_ENG_TOPIC }
    send_to_topic(message, correlation_id, MT_COMMAND_REPLY,
                  topic=config.KAFKA_SPEAKER_TOPIC,
                  extra=reply_to)


def send_sync_rules_response(added_rules, not_deleted, proper_rules,
                             correlation_id):
    message = Message()
    message.clazz = 'org.openkilda.messaging.info.switches.SyncRulesResponse'
    message.added_rules = list(added_rules)
    message.not_deleted = list(not_deleted)
    message.proper_rules = list(proper_rules)
    send_to_topic(message, correlation_id, MT_INFO,
                  destination="NORTHBOUND",
                  topic=config.KAFKA_NORTHBOUND_TOPIC)


def send_force_install_commands(switch_id, flow_commands, correlation_id):
    message = Message()
    message.clazz = 'org.openkilda.messaging.command.switches.InstallMissedFlowsRequest'
    message.switch_id = switch_id
    message.flow_commands = flow_commands
    send_to_topic(message, correlation_id, MT_COMMAND,
                  topic=config.KAFKA_SPEAKER_TOPIC)


class Message(object):
    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__)

    def add(self, vals):
        self.__dict__.update(vals)


def send_to_topic(payload, correlation_id,
                  message_type,
                  destination="WFM",
                  topic=config.KAFKA_FLOW_TOPIC,
                  extra=None):
    """
    :param extra: a dict that will be added to the message. Useful for adding reply_to for Command With Reply.
    """
    message = Message()
    message.payload = payload
    message.clazz = message_type
    message.destination = destination
    message.timestamp = get_timestamp()
    message.correlation_id = correlation_id
    if extra:
        message.add(extra)
    kafka_message = b'{}'.format(message.to_json())
    logger.debug('Send message: topic=%s, message=%s', topic, kafka_message)
    message_result = producer.send(topic, kafka_message)
    message_result.get(timeout=5)


def send_info_message(payload, correlation_id):
    send_to_topic(payload, correlation_id, MT_INFO)


def send_cache_message(payload, correlation_id):
    send_to_topic(payload, correlation_id, MT_INFO, "WFM_CACHE", config.KAFKA_CACHE_TOPIC)


def send_error_message(correlation_id, error_type, error_message,
                       error_description, destination="WFM",
                       topic=config.KAFKA_FLOW_TOPIC):
    # TODO: Who calls this .. need to pass in the right TOPIC
    data = {"error-type": error_type,
            "error-message": error_message,
            "error-description": error_description,
            "clazz": MT_ERROR_DATA}
    send_to_topic(data, correlation_id, MT_ERROR, destination, topic)


def send_install_commands(flow_rules, correlation_id):
    """
    flow_utils.get_rules() creates the flow rules starting with ingress, then transit, then egress. For the install,
    we would like to send the commands in opposite direction - egress, then transit, then ingress.  Consequently,
    the for logic should go in reverse
    """
    for flow_rule in reversed(flow_rules):
        # TODO: (same as delete todo) Whereas this is part of the current workflow .. feels like we should have the workflow manager work
        #       as a hub and spoke ... ie: send delete to FL, get confirmation. Then send delete to DB, get confirmation.
        #       Then send a message to a FLOW_EVENT topic that says "FLOW CREATED"

        send_to_topic(flow_rule, correlation_id, MT_COMMAND,
                      destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        # FIXME(surabujin): WFM reroute this message into CONTROLLER
        send_to_topic(flow_rule, correlation_id, MT_COMMAND,
                      destination="WFM", topic=config.KAFKA_FLOW_TOPIC)


def send_delete_commands(nodes, correlation_id):
    """
    Build the message for each switch node in the path and send the message to both the speaker and the flow topic

    :param nodes: array of dicts: switch_id; flow_id; cookie
    :return:
    """

    logger.debug('Send Delete Commands: node count=%d', len(nodes))
    for node in nodes:
        data = build_delete_flow(str(node['switch_id']), str(node['flow_id']), node['cookie'])
        # TODO: Whereas this is part of the current workflow .. feels like we should have the workflow manager work
        #       as a hub and spoke ... ie: send delete to FL, get confirmation. Then send delete to DB, get confirmation.
        #       Then send a message to a FLOW_EVENT topic that says "FLOW DELETED"
        send_to_topic(data, correlation_id, MT_COMMAND,
                      destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        send_to_topic(data, correlation_id, MT_COMMAND,
                      destination="WFM", topic=config.KAFKA_FLOW_TOPIC)


ChunkDescriptor = collections.namedtuple(
    'ChunkDescriptor', ('current', 'total'))
DumpEntity = collections.namedtuple(
    'DumpEntity', ('need_space', 'key', 'payload'))


def send_network_dump(
        correlation_id, switch_set, isl_set, flow_set, size_limit=32 * Kb):
    optimal_size = int(size_limit * .95)

    some_big_index = 1 << 32
    payload_template = {
        'chunk': ChunkDescriptor(some_big_index, some_big_index),
        'switches': [],
        'isls': [],
        'flows': [],
        'clazz': MT_NETWORK}

    wrapper_overhead = len(json.dumps(payload_template))
    dump_chunks = []
    for key, data in (
            ('switches', switch_set),
            ('isls', isl_set),
            ('flows', flow_set)):

        for payload in data:
            packed = json.dumps(payload)
            dump_chunks.append(DumpEntity(len(packed), key, payload))

    dump_chunks.sort(key=lambda x: x.need_space, reverse=True)

    last_is_empty = False
    produced_chunks = []
    while dump_chunks:
        current = copy.deepcopy(payload_template)
        left_place = optimal_size - wrapper_overhead

        processed = []
        for idx, entity in enumerate(dump_chunks):
            if left_place - entity.need_space < 0:
                continue

            current[entity.key].append(entity.payload)
            left_place -= entity.need_space
            processed.insert(0, idx)

        for idx in processed:
            del dump_chunks[idx]

        if not processed and last_is_empty:
            raise ValueError((
                'Can\'t fit any more entities in chunks with size '
                'limit {}({}). Size of smallest left entity: {}').format(
                    size_limit, optimal_size, dump_chunks[-1].need_space))

        last_is_empty = not bool(processed)
        produced_chunks.append(current)

    if not produced_chunks:
        logger.debug('There is no any entity in produced network dump')
        produced_chunks.append(payload_template)

    for idx, chunk in enumerate(produced_chunks):
        descriptor = ChunkDescriptor(
            idx + 1, len(produced_chunks))
        chunk['chunk'] = descriptor._asdict()
        logger.debug('Send network dump [{}/{}]'.format(
            descriptor.current, descriptor.total))
        send_cache_message(chunk, correlation_id)
