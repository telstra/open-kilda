#!/usr/bin/python
# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

from __future__ import absolute_import

import os

from bottle import get, post, request, run, Bottle, response, request, install
from mininet.net import Mininet
from mininet.node import RemoteController, OVSKernelSwitch, Host, OVSSwitch
from mininet.clean import cleanup
from mininet.link import TCLink
from mininet.util import errRun
from mininet.topolib import TorusTopo

from jsonschema import validate
import logging
from logging.config import dictConfig
import json
import socket
import Queue
import threading
import time
import subprocess
from functools import wraps

########################
## GLOBALS
########################

net = None  # mininet object

class KildaSwitch( OVSSwitch ):
    "Add the OpenFlow13 Protocol"
    def __init__(self,name,**params):
        params['protocols'] = 'OpenFlow13'
        OVSSwitch.__init__(self, name, **params)

    @classmethod
    def batchStartup( cls, switches, run=errRun ):
        """
        Mininet looks for this during stop(). It exists in OVSSwitch. Kilda, at the moment,
        doesn't like this batch operation (and it shouldn't be done in batch)
        """
        logger.info ("IGNORE batchStartup()")
        for switch in switches:
            if switch.batch:
                logger.warn (" .... BATCH = TRUE !!!!!!")
        return switches

    @classmethod
    def batchShutdown( cls, switches, run=errRun ):
        """
        Mininet looks for this during stop(). It exists in OVSSwitch. Kilda, at the moment,
        doesn't like this batch operation (and it shouldn't be done in batch)
        """
        logger.info ("IGNORE batchShutdown()")
        for switch in switches:
            if switch.batch:
                logger.warn (" .... BATCH = TRUE !!!!!!")
        return switches


def log_to_logger(fn):
    '''
    Wrap a Bottle request so that a log line is emitted after it's handled.
    (This decorator can be extended to take the desired logger as a param.)
    '''
    @wraps(fn)
    def _log_to_logger(*args, **kwargs):
        actual_response = fn(*args, **kwargs)
        logger.info('%s %s %s %s' % (request.remote_addr,
                                    request.method,
                                    request.url,
                                    response.status))
        return actual_response
    return _log_to_logger


def required_parameters(*pars):
    def _hatch(__):
        def _hatchet():
            for _ in pars:
                if request.query.get(_) is None:
                    response.status = 500
                    return "%s: %s must be specified\n" % (request.path, _)
            return __(dict([(_, request.query.get(_)) for _ in pars]))
        return _hatchet
    return _hatch


install(log_to_logger)

topology_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "controllers": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "host": {
            "type": "string"
          },
          "port": {
            "type": "integer"
          }
        },
        "required": [
          "name",
          "host",
          "port"
        ]
      }
    },
    "links": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "node1": {
            "type": "string"
          },
          "node1_port": {
            "type": "integer"
          },
          "node2": {
            "type": "string"
          },
          "node2_port": {
            "type": "integer"
          }
        },
        "required": [
          "node1",
          "node2"
        ]
      }
    },
    "switches": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "dpid": {
            "type": "string"
          }
        },
        "required": [
          "name",
          "dpid"
        ]
      }
    }
  },
  "required": [
    "controllers",
    "links",
    "switches"
  ]
}

switches_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "switches": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "dpid": {
            "type": "string"
          }
        },
        "required": [
          "name",
          "dpid"
        ]
      }
    }
  },
  "required": [
    "switches"
  ]
}

links_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "links": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "node1": {
            "type": "string"
          },
          "node2": {
            "type": "string"
          }
        },
        "required": [
          "node1",
          "node2"
        ]
      }
    }
  },
  "required": [
    "links"
  ]
}

controllers_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "controllers": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "host": {
            "type": "string"
          },
          "port": {
            "type": "integer"
          }
        },
        "required": [
          "name",
          "host",
          "port"
        ]
      }
    }
  },
  "required": [
    "controllers"
  ]
}

torus_topo_schema = {
    "$schema": "http://json-schema.org/draft-04/schema#",
    "type": "object",
    "properties": {
        "controller": {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "host": {
                    "type": "string"
                },
                "port": {
                    "type": "integer"
                }
            },
            "required": [
                "name",
                "host",
                "port"
            ]
        },
        "torus": {
            "type": "object",
            "properties": {
                "x": {
                    "type": "integer"
                },
                "y": {
                    "type": "integer"
                }
            },
            "required": [
                "x",
                "y"
            ]
        }
    },
    "required": [
        "controller",
        "torus"
    ]
}


def controller_info(controller):
    return {"name": controller.name,
            "host": controller.ip,
            "port": controller.port}


def list_controllers():
    return [controller_info(x) for x in net.controllers]


def switch_info(switch):
    intfs = []
    if len(switch.intfs) > 0:
        for i in switch.intfs:
            intf = switch.intfs[i]
            intfs.append({'name': intf.name,
                          'mac': intf.mac,
                          'status': intf.status()})
    return {'name': switch.name,
            'dpid': switch.dpid,
            'connected': switch.connected(),
            'interface': intfs}


def list_switch(name):
    return (switch_info(net.switches[name]))


def list_switches():
    return [switch_info(x) for x in net.switches]


def link_name(link):
    name = "{}:{}".format(link.intf1.name, link.intf2.name)
    if link.intf1.name < link.intf2.name:
        name = "{}:{}".format(link.intf2.name, link.intf1.name)
    return name


def link_info(link):
    return {'name': link_name(link), 'status': link.status()}


def list_links():
    return [link_info(x) for x in net.links]


@post('/topology')
def new_topology():
    global net

    logger.info( "*** Creating Topology" )
    validate(request.json, topology_schema)
    net = Mininet( controller=RemoteController, switch=KildaSwitch, build=False )

    logger.info( "" )
    logger.info( "*** Creating (Remote) controllers" )
    for controller in request.json['controllers']:
        name = controller['name']
        host = controller['host']
        port = controller['port']
        logger.info("===> adding controller name={}, host={}, port={}".format(name, host, port))
        ip = socket.gethostbyname(host)
        net.addController (name, ip=ip, port=port)

    logger.info( "" )
    logger.info( "*** Creating switches" )
    for switch in request.json['switches']:
        name = switch['name']
        dpid = switch['dpid']
        if type(dpid) is unicode:
            dpid = dpid.encode('ascii','ignore')
        logger.info("===> adding switch name={}, dpid={}".format(name, dpid))
        net.addSwitch( name=name, dpid=dpid )

    logger.info( "" )
    logger.info( "*** Creating Switch:Switch links" )
    for link in request.json['links']:
        node1 = link['node1']
        node2 = link['node2']
        logger.info("===> adding link {} -> {}".format(node1, node2))
        net.addLink( node1, node2, port1 = link.get('node1_port'), port2 = link.get('node2_port') )

    logger.info( "" )
    logger.info( "*** Creating hosts\n" )
    for switch in net.switches:
        # add single host per switch .. sufficient for our strategy of testing flows
        h = net.addHost( 'h%s' % switch.name)
        net.addLink(h, switch)

    logger.info( "" )
    logger.info( "*** Starting network" )
    net.configHosts()
    net.start()

    response.content_type = 'application/json'
    result = json.dumps({'controllers': [controller_info(x) for x in net.controllers],
                         'switches': [switch_info(x) for x in net.switches],
                         'links': [link_info(x) for x in net.links]})
    logger.info( "" )
    logger.info ("*** returning {}".format(result))
    logger.info( "" )
    return result


@post('/torus_topology')
def create_torus_topology():
    validate(request.json, torus_topo_schema)
    controller = RemoteController(name=request.json['controller']['name'],
                                  ip=socket.gethostbyname(request.json['controller']['host']),
                                  port=request.json['controller']['port'])
    topo = TorusTopo(x=request.json['torus']['x'],y=request.json['torus']['y'])
    net = Mininet(topo=topo, switch=OVSSwitch, controller=controller)
    net.start()
    return json.dumps({'status': 'Created Torus topology of requested size'})


@get('/switch/<name>')
def get_switch(name):
    response.content_type = 'application/json'
    return json.dumps(list_switch(name))


@get('/switch')
def get_switches():
    response.content_type = 'appliation/json'
    return json.dumps({'switches': list_switches()})


@post('/switch')
def create_switches():
    validate(request.json, switches_schema)
    add_switches(request.json['switches'])
    response.content_type = 'application/json'
    return json.dumps(list_switches())


@get('/links')
def get_links():
    response.content_type = 'application/json'
    return json.dumps({"links": list_links()})


@post('/links')
def create_links():
    validate(request.json, links_schema)
    add_links(request.json['links'])
    response.content_type = 'application/json'
    return json.dumps({"links": list_links()})


@get('/controllers')
def get_controllers():
    response.content_type = 'application/json'
    return json.dumps(list_controllers())


@post('/controller')
def create_controller():
    validate(request.json, controllers_schema)
    add_controllers(request.json['controllers'])
    response.content_type = 'application/json'
    return json.dumps(list_controllers())


@post('/cleanup')
def mininet_cleanup():
    global net
    logger.info( "*** Clean Topology" )
    if net is not None:
        logger.info( "--> calling mininet.stop()" )
        net.stop()
        net = None
    cleanup()
    return {'status': 'ok'}


@get('/status')
def status():
    return {'status': 'ok'}


def start_server(interface, port):
    run(host='0.0.0.0', port=port, debug=True)


#######################################
#######################################
#
# Flow Debugging Section
#   - these endpoints give the ability to create rules directly, without kilda
#   - this ability allows the developer to test flows directly (useful for development scenarios)
#
#######################################
#######################################

def get_output_actions(in_vlan,out_vlan):
    """
    This is to setup rules for host to switch / switch to host.
    It honors what we are trying to accomlish with testing Kilda:
        1) Kilda will put rules on one or more switches
        2) This code will put rules on the switches outside that set. For instance, if we are
            testing Kilda in a single switch scenario (s3), then this code will be used
            to put rules on s2 and s4 so that s3 can be tested properly.
        3) To keep things simple, we leverage the host attached to s2 and s4 to do a ping
        4) These rules setup the host to switch port as in, and then the switch to be tested as the
            out port.
    """
    result = ""
    if out_vlan == 0:
        if in_vlan != 0:
            result = "strip_vlan,"
    else:
        if in_vlan == 0:
            result = "push_vlan:0x8100,mod_vlan_vid:{},".format(out_vlan)
        else:
            result = "mod_vlan_vid:{},".format(out_vlan)
    return result


def add_single_switch_rules(switch_id,in_port,out_port,in_vlan=0,out_vlan=0):
    """add reciprocal rules to a switch to emulate kilda rules"""

    logger.info("** Adding flows to {}".format(switch_id))

    in_match  = "" if in_vlan == 0 else ",dl_vlan={}".format(in_vlan)
    out_match = "" if out_vlan == 0 else ",dl_vlan={}".format(out_vlan)

    in_action = get_output_actions(in_vlan,out_vlan)
    out_action = get_output_actions(out_vlan,in_vlan)

    noise = "idle_timeout=0,priority=1000"
    in_rule = "{},in_port={}{},actions={}output:{}".format(noise, in_port, in_match, in_action, out_port)
    out_rule = "{},in_port={}{},actions={}output:{}".format(noise, out_port, out_match, out_action, in_port)
    print("ingress rule: {}".format(in_rule))
    print("egress rule: {}".format(out_rule))
    # Ingress
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow",switch_id,in_rule],
                     stdout=subprocess.PIPE).wait()
    # Egress
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow",switch_id,out_rule],
                     stdout=subprocess.PIPE).wait()

    # ## If debugging, remove the comments below to see what the flow rules are
    # result = subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","dump-flows",switch_id],
    #                           stdout=subprocess.PIPE).communicate()[0]
    # logger.info(result)


def clear_single_switch_rules(switch_id,in_port,out_port):
    """remove rules from switch 3 to emulate kilda clear rules"""
    print("** Remove flows from {}".format(switch_id))
    in_rule = "in_port={}".format(in_port)
    out_rule = "in_port={}".format(out_port)
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","del-flows",switch_id,in_rule],
                     stdout=subprocess.PIPE).wait()
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","del-flows",switch_id,out_rule],
                     stdout=subprocess.PIPE).wait()

    ### If debugging, remove the comments below to see what the flow rules are
    # result = subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","dump-flows",switch_id],
    #                           stdout=subprocess.PIPE).communicate()[0]
    # print (result)


def pingable(host1, host2):
    result = host1.cmd( 'ping -c1 -w1 %s' % (host2.IP()) )
    lines = result.split("\n")
    if "1 packets received" in lines[3]:
        print "CONNECTION BETWEEN ", host1.IP(), "and", host2.IP()
        return True
    else:
        print "NO CONNECTION BETWEEN ", host1.IP(), "and", host2.IP()
        return False


@get('/checkpingtraffic')
@required_parameters("srcswitch", "dstswitch", "srcport", "dstport", "srcvlan",
                     "dstvlan")
def check_ping_traffic(p):
    """
    Algorithm:
        1) add host/switch ingress/egress rules on Src and Dst
        2) do the ping
        3) remove host/switch ingress/egress rules on Src and Dst
    """
    # initial example:
    #
    #   - test switch 3, using switches 2 and 4.  s2 and s4 are sent in.
    # - single switch "3" inport 1, outport 2
    # - switch 2 needs to push packet from port 3 (h)  to port 2 (s3), matching switch 3 rules
    # - switch 2 needs to push packet from port 2 (s3) to port 3 (h) , should strip
    # - switch 4 needs to push packet from port 3 (h)  to port 1 (s3), matching switch 3 rules
    # - switch 4 needs to push packet from port 1 (s3) to port 3 (h) , should strip
    src_switch = p['srcswitch']
    src_port = p['srcport']
    src_vlan = int(p['srcvlan'])
    dst_switch = p['dstswitch']
    dst_port = p['dstport']
    dst_vlan = int(p['dstvlan'])

    logger.info( "** PING request received: src={}:{}x{}  dst={}:{}x{}".format(
        src_switch,src_port,src_vlan,dst_switch,dst_port,dst_vlan
    ))

    # TODO: better to find the host port, vs encoding the heuristic
    src_host_port = 2 if src_switch == "00000001" else 3  # all hosts are on port 3, except first switch
    dst_host_port = 3

    logger.info ( "--> adding host/switch rules" )
    # for src port (ingress): inport = 2 or 3, no vlan ... send to srcport,srcvlan
    # for src port (egress): Opposite of ingress .. inport = srcport,srcvlan ... 2 or 3, no vlan
    # for dst port .. same strategy
    add_single_switch_rules( src_switch, src_host_port, src_port, 0, src_vlan )
    add_single_switch_rules( dst_switch, dst_host_port, dst_port, 0, dst_vlan )

    logger.info ( "--> ping" )
    src_host = net.nameToNode["h%s" % src_switch]
    dst_host = net.nameToNode["h%s" % dst_switch]
    successful_ping = pingable(src_host, dst_host)

    logger.info ( "--> remove host/switch rules" )
    clear_single_switch_rules( src_switch, src_host_port, src_port )
    clear_single_switch_rules( dst_switch, dst_host_port, dst_port )

    if successful_ping:
        response.status = 200
        return "True"
    else:
        response.status = 503
        return "False"


ofctl_start='ovs-ofctl -O OpenFlow13 add-flow'

@get("/add_default_flows")
@required_parameters("switch")
def add_default_flows(p):
    switch = p['switch']
    cmd1 = "%s %s idle_timeout=0,priority=1,actions=drop" % (ofctl_start, switch)
    cmd2 = "%s %s idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller" % (ofctl_start, switch)
    result1 = os.system(cmd1)
    result2 = os.system(cmd2)
    return {'result1': result1, 'result2': result2}


@get("/add_ingress_flow")
@required_parameters("switch","inport","vlan","outport","priority")
def add_ingress_flow(p):
    switch = p['switch']
    inport = p['inport']
    vlan = p['vlan']
    outport = p['outport']
    priority = p['priority']

    cmd1 = "%s %s idle_timeout=0,priority=%s,in_port=%s,actions=push_vlan:0x8100,mod_vlan_vid:%s,output:%s" % \
           (ofctl_start, switch,priority,inport,vlan,outport)
    result1 = os.system(cmd1)
    return {'result1': result1}


@get("/add_egress_flow")
@required_parameters("switch","inport","vlan","outport","priority")
def add_egress_flow(p):
    switch = p['switch']
    inport = p['inport']
    vlan = p['vlan']
    outport = p['outport']
    priority = p['priority']

    cmd1 = "%s %s idle_timeout=0,priority=%s,in_port=%s,dl_vlan=%s,actions=strip_vlan,output:%s" % \
           (ofctl_start, switch,priority,inport,vlan,outport)
    result1 = os.system(cmd1)
    return {'result1': result1}


@get("/add_transit_flow")
@required_parameters("switch","inport","outport")
def add_transit_flow(p):
    switch = p['switch']
    inport = p['inport']
    outport = p['outport']

    cmd1 = "%s %s in_port=%s,actions=output=%s" % \
           (ofctl_start, switch,inport,outport)
    result1 = os.system(cmd1)
    return {'result1': result1}


@get("/remove_transit_flow")
@required_parameters("switch","inport")
def remove_transit_flow(p):
    switch = p['switch']
    inport = p['inport']

    cmd1 = "ovs-ofctl -O OpenFlow13 del-flows %s in_port=%s" % \
           (switch,inport)
    result1 = os.system(cmd1)
    return {'result1': result1}


# Declare the variables before the init
def init():
    """Get the global variables defined and initialized"""
    global logger

    with open("/app/log.json", "r") as fd:
        logging.config.dictConfig(json.load(fd))

    logger = logging.getLogger()


def main():
    init()
    start_server('0.0.0.0', 38080)


if __name__ == '__main__':
    main()
