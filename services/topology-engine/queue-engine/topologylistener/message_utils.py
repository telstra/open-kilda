import time
import json
from kafka import KafkaProducer

from logger import get_logger


topic = 'kilda-test'
bootstrapServer = 'kafka.pendev:9092'
producer = KafkaProducer(bootstrap_servers=bootstrapServer)
logger = get_logger()


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
    flow.command = "install_ingress_flow"
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
    flow.command = "install_egress_flow"
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
    flow.command = "install_transit_flow"
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
    flow.command = "install_one_switch_flow"
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
    flow.command = "delete_flow"
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


def send_message(payload, correlation_id, message_type, destination="WFM"):
    message = Message()
    message.payload = payload
    message.type = message_type
    message.destination = destination
    message.timestamp = get_timestamp()
    message.correlation_id = correlation_id
    kafka_message = b'{}'.format(message.to_json())
    logger.info('Send message: topic=%s, message=%s',topic, kafka_message)
    message_result = producer.send(topic, kafka_message)
    message_result.get(timeout=5)


def send_error_message(correlation_id, error_type, error_message,
                       error_description, destination="WFM"):
    data = {"error-type": error_type,
            "error-message": error_message,
            "error-description": error_description}
    send_message(data, correlation_id, "ERROR", destination)


def send_install_commands(flow_rules, correlation_id):
    for flow_rule in flow_rules:
        send_message(flow_rule, correlation_id, "COMMAND")


def send_delete_commands(switches, flow_id, correlation_id, cookie):
    for switch in switches:
        data = build_delete_flow(switch, str(flow_id), cookie)
        send_message(data, correlation_id, "COMMAND")
