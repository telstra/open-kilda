#!/usr/bin/env python
"""
mininet_rest_test.py: Validate mininet_rest works within the mininet framework.

    This will launch an example network, with switches and hosts and rules, confirming that
    ping will work across hosts that are connected properly.

Dependencies:
    This class depends on a controller - it is best to launch both mininet and floodlight.
    $ docker-compose up -d mininet floodlight

    If possible, bring up the entire kilda controller.

Usage (example uses VLAN ID=1000):
    From the command line:
        cd /; python -m app.examples.mininet_rest_test
        (run as module so that switch_and_host can import mininet_rest)
"""

from .. import mininet_rest
import pprint

from bottle import get, post, request, run, Bottle, response, request, install
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.node import RemoteController, OVSKernelSwitch, Host
from mininet.node import OVSController
from mininet.clean import cleanup
from mininet.link import TCLink, Link
from jsonschema import validate
import logging
from logging.config import dictConfig
import json
import socket
import Queue
import threading
import random
import time
from functools import wraps
from datetime import datetime


topo_json = {
    "controllers": [
        {
            "host": "kilda",
            "name": "floodlight",
            "port": 6653
        }
    ],
    "links": [
        {
            "node1": "00000001",
            "node2": "00000002"
        },
        {
            "node1": "00000002",
            "node2": "00000003"
        },
        {
            "node1": "00000003",
            "node2": "00000004"
        },
        {
            "node1": "00000004",
            "node2": "00000005"
        }
    ],
    "switches": [
        {
            "dpid": "deadbeef00000001",
            "name": "00000001"
        },
        {
            "dpid": "deadbeef00000002",
            "name": "00000002"
        },
        {
            "dpid": "deadbeef00000003",
            "name": "00000003"
        },
        {
            "dpid": "deadbeef00000004",
            "name": "00000004"
        },
        {
            "dpid": "deadbeef00000005",
            "name": "00000005"
        }
    ]
}

################################################
################################################
#
# TODO: This needs to be re-written after mininet_rest is adjusted to leverage the model from pland.
#
################################################
################################################

def debug(msg, *args):
    logger.debug(msg, args)


def cleanup():
    mininet_rest.mininet_cleanup();

hosts = {}
links = {}


def add_host(sname, hname='h1', hip='10.0.0.1'):
    switch = mininet_rest.switches[sname]
    hostname = "{}_{}".format(hname,sname[-2:])
    logger.debug("hostname = {}".format(hostname))
    host = Host(hostname)
    link = TCLink(host, switch)
    host.setIP(hip,prefixLen=24,intf=link.intf1)
    link.intf2.node.attach(link.intf2)
    hosts[hostname] = host
    links[hostname] = link
    return host, link, switch

def add_hosts():
    host, link, switch = add_host('00000001', hname='h1', hip='10.0.0.1')
    logger.debug("intf1.node.IP = {}".format(link.intf1.node.IP()))
    logger.debug("intf2.node.IP = {}".format(link.intf2.node.IP()))
    logger.debug("host   = {}".format(host.__dict__))
    logger.debug("switch = {}".format(switch.__dict__))

    host2, link2, switch2 = add_host('00000002', hname='h2', hip='10.0.0.2')

    result = host.cmd( 'ping -c1 %s' % (switch.IP()) )
    debug("result h1 to s1 = %s", result)

    result = host.cmd( 'ping -c1 %s' % (host2.IP()) )
    debug("result h1 to s2 = %s", result)


def create_topology():
    mininet_rest.add_controllers(topo_json['controllers'])
    mininet_rest.add_switches(topo_json['switches'])
    mininet_rest.add_links(topo_json['links'])
    add_hosts()


def list_topology():
    pprint.pprint(mininet_rest.list_switches())


def add_controller(name, host, port):
    logger.debug("adding controller name={}, host={}, port={}"
                 .format(name, host, port))
    ip = socket.gethostbyname(host)
    controller = RemoteController(name, ip=ip, port=port)
    controller.start()

def planb():
    mini = Mininet()
    host = "kilda"
    ip = socket.gethostbyname(host)
    port = 6653
    name = 'floodlight'

    controller = RemoteController(name, ip=ip, port=port)
    controller.start()

    mini.addController(controller)
    s1 = mini.addSwitch('s1')
    s2 = mini.addSwitch('s2')
    h1 = mini.addHost('h1')
    h2 = mini.addHost('h2')
    mini.addLink(h1,s1)
    mini.addLink(h2,s2)
    mini.addLink(s1,s2)
    h1.configDefault()
    h2.configDefault()
    mini.start()
    CLI( mini )
    mini.ping([h1,h2])
    mini.stop()


def main():
    global logger
    logger = logging.getLogger()

    mininet_rest.init()
    cleanup()
    # create_topology()
    # list_topology()
    planb()
    cleanup()


# example of ping:
# ================
# result = node.cmd( 'ping -c1 %s %s' % (opts, dest.IP()) )
# sent, received = self._parsePing( result )


if __name__ == '__main__':
    main()
