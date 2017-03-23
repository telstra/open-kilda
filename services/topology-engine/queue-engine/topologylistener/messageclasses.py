#!/usr/bin/python
import json
import db
from jsonschema import validate
from py2neo import Node, Relationship

graph = db.create_p2n_driver()

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
            if self.get_message_type() == "isl":
                eventHandled = self.create_isl()
            if self.get_message_type() == "switch" and self.data['state'] == "DEACTIVATED":
                eventHandled = self.remove_switch()
            return eventHandled
        except Exception as e:
            print e

    def create_switch(self):
        switchid = self.data['switch_id']
        switchExists = graph.find_one('switch', property_key='name', property_value='{}'.format(switchid))
        if not switchExists:
            switch = Node("switch", name="{}".format(switchid))
            graph.create(switch)
            return True
        else:
            print "Switch '{0}' already exists in topology.".format(switchid)
            return True

    def remove_switch(self):
        switchid = self.data['switch_id']
        switch = graph.find_one('switch', property_key='name', property_value='{}'.format(switchid))
        if switch:
            out_rels = graph.match(start_node=switch, rel_type='isl')
            in_rels = graph.match(end_node=switch, rel_type='isl')
            for rel in out_rels:
                graph.separate(rel)
            for rel in in_rels:
                graph.separate(rel)
            graph.delete(switch)
            return True
        else:
            print "Switch '{0}' does not exist in topology.".format(switchid)
            return True

    def create_isl(self):
        path = self.data['path']
        a_switch = path[0]['switch_id']
        a_port = path[0]['port_no']
        b_switch = path[1]['switch_id']
        b_port = path[1]['port_no']

        a_switchNode = graph.find_one('switch', property_key='name', property_value='{}'.format(a_switch))
        b_switchNode = graph.find_one('switch', property_key='name', property_value='{}'.format(b_switch))

        if not a_switchNode or not b_switchNode:
            return False

        isl = Relationship(a_switchNode, "isl", b_switchNode, src_port=a_port, dst_port=b_port, src_switch=a_switch, dst_switch=b_switch)
        graph.create(isl)


