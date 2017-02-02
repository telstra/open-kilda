#!/usr/bin/python

print "Topology engine started."

import topologylistener
import topologyrest

import threading
import time

threading.Thread(target=topologylistener.functions.listen_for_topology_event()).start()    

