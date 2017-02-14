#!/usr/bin/python

print "Topology engine starting."

import topologylistener


import threading
import time

while True:
    try:
        print "Starting lister thread."
        threading.Thread(target=topologylistener.functions.listen_for_topology_event()).start()
        print "Listener thread closed."
    except Exception as e:
        print "Listener thread unhandled exception:"
        print e
