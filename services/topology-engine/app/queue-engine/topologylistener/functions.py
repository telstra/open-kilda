import db, kafkareader
import json
import time
import threading

from pprint import pprint
from messageclasses import MessageItem

print "Topology engine started."

def get_event():
    consumer = kafkareader.create_consumer()
    while True:
        rawevent = kafkareader.read_message(consumer)
        j = json.loads(rawevent)
        event = MessageItem(**j)
        t = threading.Thread(target=topo_event_handler, args=(event,))
        t.daemon =True
        t.start()

def topo_event_handler(event):
    eventHandled = False
    while not eventHandled:
        if event.data['message_type'] == "switch" and event.data['state'] == "ADDED":
            
            eventHandled = create_switch(event.data['switch_id'])
        if event.data['message_type'] == "isl":
            eventHandled = create_isl(event.data['path'])
        time.sleep(1)

def create_switch(switchid):
    print "Creating switch '{0}'.".format(switchid)
    switchExists = bool(db.runner("MATCH (a:switch{{ name: '{0}' }}) return count(a)".format(switchid)).single()[0])
    if not switchExists:
        query = "CREATE (:switch {{ name: '{0}' }})".format(switchid)
        db.runner(query)
        print "Event: switch added to topology"
        return True
    else:
        print "Switch '{0}' already exists in topology.".format(switchid)
        return True


def create_isl(path):
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
        print "Event: isl added to topology"
        return True
    else:
        print "ISL {0}:{1} -> {2}:{3} already exists.".format(a_switch, a_port, b_switch, b_port)
        return True
    