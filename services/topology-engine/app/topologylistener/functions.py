import db
import kafkareader
import json
import time

from pprint import pprint

db.runner("MATCH (n) DETACH DELETE n")

def get_event():
    rawevent = kafkareader.readMessage()
    return json.loads(rawevent)

def listen_for_topology_event():
    while True:
        event = get_event()
        if event:
            if event['message-type'] == "SWITCH" and event['data']['event-type'] == "ADDED":
                print "Event: switch added to topology"
                create_switch(event['data']['switch-id'])
            if event['message-type'] == "PATH" and event['data']['type'] == "ISL":
                print "Event: isl added to topology"

                switchA = event['data']['links'][0]['nodes'][0]['switch']
                portA = event['data']['links'][0]['nodes'][0]['port']
                switchB = event['data']['links'][0]['nodes'][1]['switch']
                portB = event['data']['links'][0]['nodes'][1]['port']

                create_isl(switchA, portA, switchB, portB)
    return 0

def create_switch(switchid):
    print "Creating switch '{0}'.".format(switchid)
    query = "CREATE (:switch {{ name: '{0}' }})".format(switchid)
    db.runner(query)
    return 0


def create_isl(a_switch, a_port, b_switch, b_port):
    query = "MATCH (a:switch{{ name: '{0}' }}),(b:switch{{ name: '{2}' }}) CREATE(a) -[r:isl {{ src_port: '{1}', dst_port: '{3}'  }}]-> (b) CREATE(b) -[s:isl {{ src_port: '{3}', dst_port: '{1}' }}]-> (a)".format(a_switch, a_port, b_switch, b_port)
    print query
    db.runner(query)

    return 0