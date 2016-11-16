import db
import json
import time

from pprint import pprint

db.runner("MATCH (n) DETACH DELETE n")


events = ['{ "type": "switch", "action": "create", "id": "1", "ports": 4 }', 
          '{ "type": "switch", "action": "create", "id": "2", "ports": 4 }', 
          '{ "type": "switch", "action": "create", "id": "3", "ports": 4 }', 
          '{ "type": "isl", "action": "create", "a_switch": "1", "a_port": 1, "b_switch": "2", "b_port": 1 }', 
          '{ "type": "isl", "action": "create", "a_switch": "3", "a_port": 3, "b_switch": "2", "b_port": 4 }', 
          '{ "type": "isl", "action": "create", "a_switch": "3", "a_port": 4, "b_switch": "1", "b_port": 3 }', 
          '{ "type": "isl", "action": "create", "a_switch": "3", "a_port": 1, "b_switch": "1", "b_port": 2 }']

eventCount = 0

def get_event():
    
    global events
    global eventCount
    

    try:
        rawevent = events[eventCount]
    except:
        time.sleep(10)
        return 0
    eventCount = eventCount + 1

    event = json.loads(rawevent)
        
    return event
    
def listen_for_topology_event():
    while True:
        event = get_event()
        print "collecting event"
        if event['type'] == "switch" and event['action'] == "create": 
            print "Event: switch added to topology"
            create_switch(event['id'], event['ports'])
        
        if event['type'] == "isl" and event['action'] == "create": 
            print "Event: isl added to topology"
            create_isl(event['a_switch'], event['a_port'], event['b_switch'], event['b_port'])
    return 0

def create_switch(switchid, switchports):
    print "Creating switch '{0}' with '{1}' ports.".format(switchid, switchports)
    query = "CREATE (:switch {{ name: '{0}' }})".format(switchid)
    db.runner(query)

    for i in xrange(switchports):
        port = str(i+1)
        query = "CREATE (:port {{ name: '{0}:{1}' }})".format(switchid,port)
        db.runner(query)

        query = "MATCH (p:port{{ name: '{0}:{1}' }}),(s:switch {{ name: '{0}' }}) CREATE(s) -[r:queue]-> (p) ".format(switchid,port)
        db.runner(query)

    return 0


def create_isl(a_switch, a_port, b_switch, b_port):
    query = "MATCH (a:port{{ name: '{0}:{1}' }}),(b:port{{ name: '{2}:{3}' }}) CREATE(a) -[r:isl]-> (b) CREATE(b) -[s:isl]-> (a)".format(a_switch, a_port, b_switch, b_port)
    print query
    db.runner(query)

    return 0