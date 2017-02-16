import db, kafkareader
import json
import time

from pprint import pprint
from messageclasses import MessageItem

#print "Cleaning old topology"
#db.runner("MATCH (n) DETACH DELETE n")

print "Topology engine started."

def get_event():
    rawevent = kafkareader.readMessage()
    j = json.loads(rawevent)
    return MessageItem(**j)

def listen_for_topology_event():
    print "Lister connected and waiting for messages"
    while True:
        event = get_event()
        if event:
            print event.toJSON()
            if event.data['message_type'] == "switch" and event.data['state'] == "ADDED":
                print "Event: switch added to topology"
                create_switch(event.data['switch_id'])
            if event.data['message_type'] == "isl":
                print "Event: isl added to topology"
                create_isl(event.data['path'])
        if not event:
                time.sleep(.5)
    return 0

def create_switch(switchid):
    print "Creating switch '{0}'.".format(switchid)
    switchExists = bool(db.runner("MATCH (a:switch{{ name: '{0}' }}) return count(a)".format(switchid)).single()[0])
    if not switchExists:
        query = "CREATE (:switch {{ name: '{0}' }})".format(switchid)
        db.runner(query)
    else:
        print "Switch '{0}' already exists in topology.".format(switchid)
    return 0


def create_isl(path):
    
    a_switch = path[0]['switch_id']
    a_port = path[0]['port_no']
    b_switch = path[1]['switch_id']
    b_port = path[1]['port_no']

    islExists = bool(db.runner("MATCH p=()-[r:isl{{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]->() RETURN count(p)".format(a_switch, a_port, b_switch, b_port)).single()[0])
    if not islExists:
        query = "MATCH (a:switch{{ name: '{0}' }}),(b:switch{{ name: '{2}' }}) CREATE(a) -[r:isl {{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]-> (b)".format(a_switch, a_port, b_switch, b_port)
        db.runner(query)
    else:
        print "ISL {0}:{1} -> {2}:{3} already exists.".format(a_switch, a_port, b_switch, b_port)
    return 0