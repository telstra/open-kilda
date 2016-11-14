#!/usr/bin/python
import neo4jFunctions as nF

def listen_for_topology_event():
    create_switch('1', 11)
    return 0

def create_switch(switchid, switchports):
    if switchports > 10:
        nF.runner("CREATE (switch{0}:switch {{ switchID: '{0}' }})".format(switchid))
    return 0

