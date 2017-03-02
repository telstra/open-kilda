import db, kafkareader
import json
import time
import threading

from datetime import datetime
from messageclasses import MessageItem
from pprint import pprint

print "Topology engine started."

def get_events():
    consumer = kafkareader.create_consumer()
    while True:
        try:
            rawevent = kafkareader.read_message(consumer)
            event = MessageItem(**json.loads(rawevent))
            print event.to_json()
            t = threading.Thread(target=topo_event_handler, args=(event,))
            t.daemon =True
            t.start()
        except Exception as e:
            print e

def topo_event_handler(event):
    eventHandled = False
    handleCount = 0
    while not eventHandled:
        eventHandled = event.handle()
        if not eventHandled:
            handleCount += 1
            if handleCount > 50:
                time.sleep(.1)
    print "{} Event processed for: {}".format("{:%d %b, %Y %H:%M:%S}".format(datetime.now()), event.get_message_type())
