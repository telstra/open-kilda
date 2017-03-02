#!/usr/bin/python
import json
import db
from jsonschema import validate


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
            return eventHandled
        except Exception as e:
            print e

    def create_switch(self):
        switchid = self.data['switch_id']
        switchExists = bool(db.runner("MATCH (a:switch{{ name: '{0}' }}) return count(a)".format(switchid)).single()[0])
        if not switchExists:
            query = "CREATE (:switch {{ name: '{0}' }})".format(switchid)
            db.runner(query)
            return True
        else:
            print "Switch '{0}' already exists in topology.".format(switchid)
            return True
    
    def create_isl(self):
        path = self.data['path']
        a_switch = path[0]['switch_id']
        a_port = path[0]['port_no']
        b_switch = path[1]['switch_id']
        b_port = path[1]['port_no']
        a_switchExists = bool(db.runner("MATCH (a:switch{{ name: '{0}' }}) return count(a)".format(a_switch)).single()[0])
        b_switchExists = bool(db.runner("MATCH (a:switch{{ name: '{0}' }}) return count(a)".format(b_switch)).single()[0])
        if not a_switchExists or not b_switchExists:
            return False
        islExists = bool(db.runner("MATCH p=()-[r:isl{{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]->() RETURN count(p)".format(a_switch, a_port, b_switch, b_port)).single()[0])
        if not islExists:
            query = "MATCH (a:switch{{ name: '{0}' }}),(b:switch{{ name: '{2}' }}) CREATE(a) -[r:isl {{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]-> (b)".format(a_switch, a_port, b_switch, b_port)
            db.runner(query)
            return True
        else:
            print "ISL {0}:{1} -> {2}:{3} already exists.".format(a_switch, a_port, b_switch, b_port)
            return True