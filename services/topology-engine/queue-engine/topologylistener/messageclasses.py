#!/usr/bin/python
import json
import db
import requests
import time
from jsonschema import validate
from py2neo import Node, Relationship
from kafka import KafkaProducer
import datetime

from flow import *


graph = db.create_p2n_driver()
bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'
producer = KafkaProducer(bootstrap_servers=bootstrapServer)

def repair_flows(switchid):
    flows = (graph.run("MATCH (n)-[r:flow]-(m) where any(i in r.flowpath where i = '{}') return r".format(switchid))).data()
    repairedFlowIDs = []
    for flow in flows:
        if flow['r']['flowid'] not in repairedFlowIDs:
            repairedFlowIDs.append(flow['r']['flowid'])
            url = "http://topology-engine-rest/api/v1/flow"
            headers = {'Content-Type': 'application/json'}
            j_data = {"src_switch":"{}".format(flow['r']['src_switch']),
                      "src_port":1,
                      "src_vlan":0,
                      "dst_switch":"{}".format(flow['r']['dst_switch']),
                      "dst_port":1, "dst_vlan":0, "bandwidth": 2000}
            result = requests.post(url, json=j_data, headers=headers)
            if result.status_code == 200:
                deleteURL = url + "/" + flow['r']['flowid']
                result = requests.delete(deleteURL)
            else:
                #create logic to alert on failed reroute
                print "Unable to reroute flow: {}".format(flow['r']['flowid'])
    return True
    


class MessageItem(object):
    def __init__(self, **kwargs):
        self.type = kwargs.get("type")
        self.timestamp = kwargs.get("timestamp")
        self.payload = kwargs.get("payload")
        # make message processable in case of no destination in body
        self.destination = kwargs.get("destination", "TOPOLOGY_ENGINE")
        self.correlation_id = kwargs.get("correlation_id", "admin-request")

    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_command(self):
        try:
            return self.payload['command']
        except:
            return "unknown"

    def get_message_type(self):
        try:
            return self.payload['message_type']
        except:
            return "unknown"

    def handle(self):
        try:
            eventHandled = False
            if self.get_message_type() == "switch" and self.payload['state'] == "ADDED":
                eventHandled = self.create_switch()
            if self.get_message_type() == "switch" and self.payload['state'] == "ACTIVATED":
                eventHandled = self.activate_switch()
            if self.get_message_type() == "isl":
                eventHandled = self.create_isl()
            if self.get_message_type() == "port":
                eventHandled = True #needs to handled correctly
            if self.get_message_type() == "switch" and self.payload['state'] == "DEACTIVATED":
                eventHandled = self.deactivate_switch()
            if self.get_command() == "flow_create":
                eventHandled = self.create_flow()
            if self.get_command() == "flow_delete":
                eventHandled = self.delete_flow()
            if self.get_command() == "flow_update":
                eventHandled = self.update_flow()
            if self.get_command() == "flow_path":
                eventHandled = self.get_flow_path()
            if self.get_command() == "flow_get":
                eventHandled = self.get_flow()
            if self.get_command() == "flows_get":
                eventHandled = self.dump_flows()
            return eventHandled
        except Exception as e:
            print e

    def activate_switch(self):
        return True

    def create_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if not switch:
            newSwitch = Node("switch", 
                             name="{}".format(switchid), 
                             state="active")
            graph.create(newSwitch)
            print "Adding switch: {}".format(switchid)
            return True
        else:
            graph.merge(switch)
            switch['state'] = "active"
            switch.push()
            print "Activating switch: {}".format(switchid)
            return True

    def deactivate_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if switch:
            graph.merge(switch)
            switch['state'] = "inactive"
            switch.push()
            print "Deactivating switch: {}".format(switchid)
            if repair_flows(switchid):
                return True
            else:
                return False

        else:
            print "Switch '{0}' does not exist in topology.".format(switchid)
            return True

    def create_isl(self):

        path = self.payload['path']
        latency = self.payload['latency_ns']
        a_switch = path[0]['switch_id']
        a_port = path[0]['port_no']
        b_switch = path[1]['switch_id']
        b_port = path[1]['port_no']

        a_switchNode = graph.find_one('switch', 
                                      property_key='name', 
                                      property_value='{}'.format(a_switch))
        b_switchNode = graph.find_one('switch', 
                                      property_key='name', 
                                      property_value='{}'.format(b_switch))

        if not a_switchNode or not b_switchNode:
            return False

        islExistsQuery = "MATCH (a:switch)-[r:isl {{src_switch: '{}', src_port: '{}', dst_switch: '{}', dst_port: '{}'}}]->(b:switch) return r"
        islExists = graph.run(islExistsQuery.format(a_switch,
                                                    a_port,
                                                    b_switch,
                                                    b_port)).data()

        if not islExists:
            islQuery = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) MERGE (u)-[:isl {{src_port: '{}', dst_port: '{}', src_switch: '{}', dst_switch: '{}', latency: '{}'}}]->(r)"
            graph.run(islQuery.format(a_switchNode['name'],
                                      b_switchNode['name'],
                                      a_port,
                                      b_port,
                                      a_switch,
                                      b_switch,
                                      latency))

            print "ISL between {} and {} created".format(a_switchNode['name'], 
                                                         b_switchNode['name'])
        else:
            islUpdateQuery = "MATCH (a:switch)-[r:isl {{src_switch: '{}', src_port: '{}', dst_switch: '{}', dst_port: '{}'}}]->(b:switch) set r.latency = {} return r"
            graph.run(islUpdateQuery.format(a_switch, 
                                            a_port, 
                                            b_switch, 
                                            b_port, 
                                            latency)).data()
            #print "ISL between {} and {} updated".format(a_switchNode['name'], 
            #                                             b_switchNode['name'])
        return True




    def create_flow(self):
        content = self.payload['payload']
        flow_id = content['flowid']
        source = content['source']
        destination = content['destination']
        transit_vlan_forward = assign_transit_vlan()
        transit_vlan_reverse = assign_transit_vlan()

        print "Create flow: correlation_id={}, flow_id={}".format(self.correlation_id, flow_id)

        (all_flows, forward_flow_switches, reverse_flow_switches) = prepare_flows(
            content, source, destination, transit_vlan_forward, transit_vlan_reverse)

        if not all_flows or not forward_flow_switches or not reverse_flow_switches:
            print "ERROR: flows were not build: all_flows={}".format(all_flows)
            return False

        send_install_commands(all_flows, self.correlation_id)

        (start, end) = find_nodes(source, destination)

        if not start or not end:
            print "ERROR: switches were not found: start_node={}, end_node={}".format(start, end)
            return False

        store_flows(start, end, content, source, destination,
                    transit_vlan_forward, transit_vlan_reverse,
                    forward_flow_switches, reverse_flow_switches)

        print 'Flow stored: flow_id={}'.format(flow_id)

        return True

    def delete_flow(self):
        flow_id = self.payload['payload']['flowid']

        print "Delete flow: correlation_id={}, flow_id={}".format(self.correlation_id, flow_id)

        switches = find_flow_switches(flow_id)

        print "Deletion flow path={}".format(switches)

        send_remove_commands(switches, flow_id, self.correlation_id)

        delete_flows_from_database_by_flow_id(flow_id)

        print 'Flow deleted: flow_id={}'.format(flow_id)

        return True

    def update_flow(self):
        content = self.payload['payload']
        flow_id = content['flowid']
        source = content['source']
        destination = content['destination']
        transit_vlan_forward = assign_transit_vlan()
        transit_vlan_reverse = assign_transit_vlan()

        print "Update flow: correlation_id={}, flow_id={}".format(self.correlation_id, flow_id)

        relationships_ids = find_flow_relationships_ids(flow_id)

        print "Deletion flow ids={}".format(relationships_ids)

        switches = find_flow_switches(flow_id)

        for relationship_id in relationships_ids:
            delete_flows_from_database_by_relationship_id(relationship_id['ID(r)'])

        (all_flows, forward_flow_switches, reverse_flow_switches) = prepare_flows(
            content, source, destination, transit_vlan_forward, transit_vlan_reverse)

        (start, end) = find_nodes(source, destination)

        if not start or not end:
            print "ERROR: switches were not found: start_node={}, end_node={}".format(start, end)
            return False

        store_flows(start, end, content, source, destination,
                    transit_vlan_forward, transit_vlan_reverse, forward_flow_switches, reverse_flow_switches)

        send_install_commands(all_flows, self.correlation_id)

        send_remove_commands(switches, flow_id, self.correlation_id)

        print 'Flow updated: flow_id={}'.format(flow_id)

        return True

    def get_flow(self):
        flow_id = self.payload['payload']['flowid']

        found_flow = find_flow_by_id(flow_id)
        flow = flow_response(found_flow[0]['r'])

        print 'Got flow={}'.format(flow)

        payload = {'payload': flow, 'message_type': "flow"}
        send_message(payload, self.correlation_id, "INFO")

        return True

    def get_flow_path(self):
        flow_id = self.payload['payload']['flowid']

        found_flow = find_flow_by_id(flow_id)
        flow = found_flow[0]['r']

        print 'Got flow={}'.format(flow)

        payload = {'payload': {'flowid': flow_id, 'flowpath': flow['flowpath']}, 'message_type': "flow_path"}
        send_message(payload, self.correlation_id, "INFO")

        return True

    def dump_flows(self):
        query = "MATCH (a:switch)-[r:flow ]->(b:switch) {} r"
        result = graph.run(query.format("return")).data()

        flows = []
        for flow in result:
            flows.append(flow_response(flow['r']))
        print 'Got flows={}'.format(flows)

        payload = {'payload': {'flow-list': flows}, 'message_type': "flows"}
        send_message(payload, self.correlation_id, "INFO")

        return True


def get_timestamp():
    return int(round(time.time() * 1000))


def send_message(payload, correlation_id, message_type):
    message = Message()
    message.payload = payload
    message.type = message_type
    message.destination = "WFM"
    message.timestamp = get_timestamp()
    message.correlation_id = correlation_id
    kafka_message = b'{}'.format(message.toJSON())
    print 'topic: {}, message: {}'.format(topic, kafka_message)
    message_result = producer.send(topic, kafka_message)
    message_result.get(timeout=5)


def send_install_commands(all_flows, correlation_id):
    for flows in all_flows:
        for flow in flows:
            send_message(flow, correlation_id, "COMMAND")


def send_remove_commands(switches, flow_id, correlation_id):
    for switch in switches:
        send_message(build_delete_flow(switch, str(flow_id)), correlation_id, "COMMAND")


def find_nodes(source, destination):
    start = graph.find_one('switch', property_key='name', property_value='{}'.format(source['switch-id']))
    end = graph.find_one('switch', property_key='name', property_value='{}'.format(destination['switch-id']))
    return start, end


def find_flow_relationships_ids(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} ID(r)"
    relationships_ids = graph.run(query.format(flow_id, "return")).data()
    return relationships_ids


def find_flow_by_id(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} r"
    flow = graph.run(query.format(flow_id, "return")).data()
    return flow


def find_flow_switches(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) return r.flowpath limit 1"
    switches = graph.run(query.format(flow_id)).evaluate()
    return switches


def delete_flows_from_database_by_flow_id(flow_id):
    query = "MATCH (a:switch)-[r:flow {{flowid: '{}'}}]->(b:switch) {} r"
    graph.run(query.format(flow_id, "delete")).data()


def delete_flows_from_database_by_relationship_id(rel_id):
    query = "MATCH (a:switch)-[r:flow]-(b:switch) WHERE id(r)={} {} r"
    graph.run(query.format(rel_id, "delete")).data()


def store_flows(start, end, content, source, destination, forward_vlan, reverse_vlan, forward_switches, reverse_switches):
    cookie = assign_cookie()
    timestamp = datetime.datetime.utcnow().isoformat()

    query = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) MERGE (u)-[:flow " \
            "{{flowid:'{}', cookie:'{}', bandwidth:'{}', " \
            "src_port: '{}', dst_port: '{}', src_switch: '{}', dst_switch: '{}', src_vlan: '{}', dst_vlan: '{}'," \
            "transit_vlan: '{}', description: '{}', last_updated: '{}', flowpath: {}}}]->(r)"

    forward_path = query.format(start['name'], end['name'], content['flowid'], cookie, content['maximum-bandwidth'],
                                source['port-id'], destination['port-id'], source['switch-id'], destination['switch-id'],
                                source['vlan-id'], destination['vlan-id'], forward_vlan, content['description'],
                                timestamp, str(forward_switches))

    reverse_path = query.format(end['name'], start['name'], content['flowid'], cookie, content['maximum-bandwidth'],
                                destination['port-id'], source['port-id'], destination['switch-id'], source['switch-id'],
                                destination['vlan-id'], source['vlan-id'], reverse_vlan, content['description'],
                                timestamp, str(reverse_switches))

    graph.run(forward_path)
    graph.run(reverse_path)
