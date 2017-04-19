import db, kafkareader
import json
import time
import threading

from datetime import datetime
from messageclasses import MessageItem
from pprint import pprint

print "Topology engine started."
handleableMessages = ['switch', 'isl', 'port']



def get_events(threadcount):
    global workerthreadcount
    print "starting thread: {}".format(threadcount)
    consumer = kafkareader.create_consumer()
    while True:
        try:
            rawevent = kafkareader.read_message(consumer)
            event = MessageItem(**json.loads(rawevent))
            if event.get_message_type() in handleableMessages:
                t = threading.Thread(target=topo_event_handler, args=(event,))
                t.daemon =True
                t.start()
            else:
                time.sleep(.1)
        except Exception as e:
            print e

def topo_event_handler(event):
    eventHandled = False
    while not eventHandled:
        eventHandled = event.handle()
        if not eventHandled:
            time.sleep(.1)
    print "{} Event processed for: {}".format("{:%d %b, %Y %H:%M:%S}".format(datetime.now()), event.get_message_type())