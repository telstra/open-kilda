#!/usr/bin/python
print "Topology engine starting."
import topologylistener

while True:
    try:
        print "Starting lister thread."
        topologylistener.functions.get_event()
    except Exception as e:
        print "Listener thread unhandled exception:"
        print e
