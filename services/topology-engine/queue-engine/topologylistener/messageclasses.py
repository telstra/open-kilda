#!/usr/bin/python
import json
import db
import requests
import time
from jsonschema import validate
from py2neo import Node, Relationship

graph = db.create_p2n_driver()

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
    def __init__(self, type, timestamp, data):
        self.type = type
        self.timestamp = timestamp
        self.data = data

    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_message_type(self):
        try:
            return self.data['message_type']
        except:
            return "unknown"

    def handle(self):
        try:
            eventHandled = False
            if self.get_message_type() == "switch" and self.data['state'] == "ADDED":
                eventHandled = self.create_switch()
            if self.get_message_type() == "switch" and self.data['state'] == "ACTIVATED":
                eventHandled = self.activate_switch()
            if self.get_message_type() == "isl":
                eventHandled = self.create_isl()
            if self.get_message_type() == "port":
                eventHandled = True #needs to handled
            if self.get_message_type() == "switch" and self.data['state'] == "DEACTIVATED":
                eventHandled = self.deactivate_switch()
            return eventHandled
        except Exception as e:
            print e

    def activate_switch(self):
        return True

    def create_switch(self):
        switchid = self.data['switch_id']
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
        switchid = self.data['switch_id']
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

        path = self.data['path']
        latency = self.data['latency_ns']
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
        



