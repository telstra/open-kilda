#!/usr/bin/python
print "Topology engine starting."
import topologylistener

while True:
    try:
        print "Starting lister thread."
        topologylistener.eventhandler.get_events()
    except Exception as e:
        print "Listener thread unhandled exception:"
        print e
