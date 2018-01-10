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

import time
import json
from kafka import KafkaProducer

import logging

import config

producer = KafkaProducer(bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS)
logger = logging.getLogger(__name__)

MT_ERROR="org.openkilda.messaging.error.ErrorMessage"
MT_COMMAND="org.openkilda.messaging.command.CommandMessage"
MT_INFO="org.openkilda.messaging.info.InfoMessage"

def get_timestamp():
    return int(round(time.time() * 1000))


class Flow(object):
    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


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


def build_delete_flow(switch, flow_id, cookie, meter_id=0):
    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.RemoveFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = switch
    flow.meter_id = meter_id

    return flow


class Message(object):
    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__, sort_keys=False, indent=4)


def send_to_topic(payload, correlation_id,
                  message_type,
                  destination="WFM",
                  topic=config.KAFKA_FLOW_TOPIC):
    message = Message()
    message.payload = payload
    message.clazz = message_type
    message.destination = destination
    message.timestamp = get_timestamp()
    message.correlation_id = correlation_id
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
            "error-description": error_description}
    send_to_topic(data, correlation_id, MT_ERROR, destination, topic)


def send_install_commands(flow_rules, correlation_id):
    for flow_rule in flow_rules:
        send_to_topic(flow_rule, correlation_id, MT_COMMAND,
                      destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        send_to_topic(flow_rule, correlation_id, MT_COMMAND,
                      destination="WFM", topic=config.KAFKA_FLOW_TOPIC)


def send_delete_commands(nodes, flow_id, correlation_id, cookie):
    logger.debug('Send Delete Commands: node count=%d', len(nodes))
    for node in nodes:
        data = build_delete_flow(str(node['switch_id']), str(flow_id), cookie)
        send_to_topic(data, correlation_id, MT_COMMAND,
                      destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        send_to_topic(data, correlation_id, MT_COMMAND,
                      destination="WFM", topic=config.KAFKA_FLOW_TOPIC)
