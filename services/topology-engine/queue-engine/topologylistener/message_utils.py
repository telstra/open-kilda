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
import logging

from kafka import KafkaProducer

from topologylistener import config
from topologylistener import const


raw_logger = logging.getLogger(__name__)

producer = KafkaProducer(bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS)

MT_ERROR = "org.openkilda.messaging.error.ErrorMessage"
MT_COMMAND = "org.openkilda.messaging.command.CommandMessage"
MT_INFO = "org.openkilda.messaging.info.InfoMessage"
MT_INFO_FLOW_STATUS = "org.openkilda.messaging.info.flow.FlowStatusResponse"
MT_ERROR_DATA = "org.openkilda.messaging.error.ErrorData"


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


def build_delete_flow(switch, flow_id, cookie, meter_id=0):
    flow = Flow()
    flow.clazz = "org.openkilda.messaging.command.flow.RemoveFlow"
    flow.transaction_id = 0
    flow.flowid = flow_id
    flow.cookie = cookie
    flow.switch_id = switch
    flow.meter_id = meter_id

    return flow


def send_to_topic(
        context, payload, message_type,
        destination="WFM", topic=config.KAFKA_FLOW_TOPIC):
    message = Message()
    message.payload = payload
    message.clazz = message_type
    message.destination = destination
    message.timestamp = make_timestamp()
    message.correlation_id = context.correlation_id

    json_data = model_to_json(message)
    json_data = json_data.encode('utf-8')

    log = context.log(raw_logger)
    if log.isEnabledFor(logging.DEBUG):
        # Unpack just packed object is the easiest way to get "raw"
        # representation of our model objects. It is expensive, but placing it
        # behind "is DEBUG level enabled" condition should protect production
        # execution from this calculations.
        json_unpacked = json.loads(json_data)
        log.debug(
            'Send message to the topic=%s (see payload in extra %s field)',
            topic, const.LOG_ATTR_JSON_PAYLOAD,
            extra={const.LOG_ATTR_JSON_PAYLOAD: json_unpacked})

    promise = producer.send(topic, json_data)
    promise.get(timeout=5)


def send_info_message(context, payload):
    send_to_topic(context, payload, MT_INFO)


def send_cache_message(context, payload):
    send_to_topic(
            context, payload, MT_INFO, "WFM_CACHE", config.KAFKA_CACHE_TOPIC)


def send_error_message(context, error_type, error_message,
                       error_description, destination="WFM",
                       topic=config.KAFKA_FLOW_TOPIC):
    # TODO: Who calls this .. need to pass in the right TOPIC
    data = {"error-type": error_type,
            "error-message": error_message,
            "error-description": error_description,
            "clazz": MT_ERROR_DATA}
    send_to_topic(context, data, MT_ERROR, destination, topic)


def send_install_commands(context, flow_rules):
    """
    flow_utils.get_rules() creates the flow rules starting with ingress, then
    transit, then egress. For the install, we would like to send the commands in
    opposite direction - egress, then transit, then ingress.  Consequently,
    the for logic should go in reverse
    """
    for flow_rule in reversed(flow_rules):
        send_to_topic(
                context, flow_rule, MT_COMMAND,
                destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        # FIXME(surabujin): WFM reroute this message into CONTROLLER
        send_to_topic(
                context, flow_rule, MT_COMMAND,
                destination="WFM", topic=config.KAFKA_FLOW_TOPIC)


def send_delete_commands(context, nodes):
    """
    Build the message for each switch node in the path and send the message to
    both the speaker and the flow topic

    :param context: current operation context
    :type context: topologylistener.context.OperationContext
    :param nodes: array of dicts: switch_id; flow_id; cookie
    :return:
    """

    context.log(raw_logger).debug(
            'Send Delete Commands (nodes count=%d)', len(nodes))
    for node in nodes:
        data = build_delete_flow(
                str(node['switch_id']), str(node['flow_id']), node['cookie'])
        send_to_topic(
                context, data, MT_COMMAND,
                destination="CONTROLLER", topic=config.KAFKA_SPEAKER_TOPIC)
        send_to_topic(
                context, data, MT_COMMAND,
                destination="WFM", topic=config.KAFKA_FLOW_TOPIC)


def model_to_json(entity, pretty=False):
    extra = {}
    if pretty:
        extra['sort_keys'] = True
        extra['indent'] = 4
    return json.dumps(entity, cls=_ModelJsonEncoder, **extra)


def make_timestamp():
    return int(time.time() * 1000)


class AbstractModel(object):
    def make_serializable_representation(self):
        return vars(self).copy()


class Flow(AbstractModel):
    pass


class Message(AbstractModel):
    pass


class _ModelJsonEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, AbstractModel):
            encoded = o.make_serializable_representation()
        else:
            encoded = super(_ModelJsonEncoder, self).default(o)
        return encoded
