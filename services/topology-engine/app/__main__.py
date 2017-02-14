#!/usr/bin/python

print "Topology engine started."

import topologylistener


import threading
import time

while True:
    try:
        threading.Thread(target=topologylistener.functions.listen_for_topology_event()).start()
    except Exception as e:
        print e
