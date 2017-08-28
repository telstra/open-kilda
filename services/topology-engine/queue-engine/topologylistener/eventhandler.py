import db, kafkareader
import json
import time
import threading

from datetime import datetime
from messageclasses import MessageItem
from pprint import pprint

print "Topology engine started."
handleableMessages = ['switch', 'isl', 'port']
handleableCommands = ['flow_create', 'flow_delete', 'flow_update', 'flow_path',
                      'flow_get', 'flows_get', 'flow_reroute', 'dump_network']

def get_events(threadcount):
    global workerthreadcount
    print "starting thread: {}".format(threadcount)
    consumer = kafkareader.create_consumer()
    while True:
        try:
            rawevent = kafkareader.read_message(consumer)
            event = MessageItem(**json.loads(rawevent))
            if "TOPOLOGY_ENGINE" != event.destination:
                print "Skip message for {}".format(event.destination)
                continue
            if event.get_message_type() in handleableMessages or event.get_command() in handleableCommands:
                print "Processing message for {}".format(event.destination)
                t = threading.Thread(target=topo_event_handler, args=(event,))
                t.daemon =True
                t.start()
        except Exception as e:
            print e

def topo_event_handler(event):
    eventHandled = False
    while not eventHandled:
        eventHandled = event.handle()
        if not eventHandled:
            print "{} Unable to process event: {}".format("{:%d %b, %Y %H:%M:%S}".format(datetime.now()), event.get_type())
            print "Message body: "
            print event.to_json()
            time.sleep(.1)
    print "{} Event processed for: {}".format("{:%d %b, %Y %H:%M:%S}".format(datetime.now()), event.get_type())
