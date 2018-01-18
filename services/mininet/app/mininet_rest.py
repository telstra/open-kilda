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


from bottle import get, post, request, run, Bottle, response, request, install
from mininet.net import Mininet
from mininet.node import RemoteController, OVSKernelSwitch, Host, OVSSwitch
from mininet.clean import cleanup
from mininet.link import TCLink
from mininet.util import errRun

from jsonschema import validate
import logging
from logging.config import dictConfig
import json
import socket
import Queue
import threading
import time
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
          "node2": {
            "type": "string"
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

    # info( "*** Creating hosts\n" )
    # hosts1 = [ net.addHost( 'h%ds1' % n ) for n in ( 1, 2 ) ]
    # hosts2 = [ net.addHost( 'h%ds2' % n ) for n in ( 1, 2 ) ]
    #
    # logger.info( "*** Creating Host:Switch links\n" )
    # for h in hosts1:
    #     net.addLink( h, s1 )
    # for h in hosts2:
    #     net.addLink( h, s2 )
    # net.configHosts()


    logger.info( "" )
    logger.info( "*** Creating Switch:Switch links" )
    for link in request.json['links']:
        node1 = link['node1']
        node2 = link['node2']
        logger.info("===> adding link {} -> {}".format(node1, node2))
        net.addLink( node1, node2 )

    logger.info( "" )
    logger.info( "*** Starting network" )
    net.start()


    response.content_type = 'application/json'
    result = json.dumps({'controllers': [controller_info(x) for x in net.controllers],
                         'switches': [switch_info(x) for x in net.switches],
                         'links': [link_info(x) for x in net.links]})
    logger.info( "" )
    logger.info ("*** returning {}".format(result))
    logger.info( "" )
    return result


#
# Create a linear topology with random links between the switches.
# - create N switches
# - create a random set of M links amongst the switches
#
@post('/create_random_linear_topology')
def create_topology():

    #
    # This code needs to be refactored to match the rest of this class.
    # What changed? We moved off of local collections of controllers / switches / links and
    #   onto the Mininet way of doing things.  This method used threading to add a bunch of
    #   switches and links in parallel .. we could look at Mininet's batch mechanism (I don't
    #   think it is any faster - it may just loop through things - so we'd need to figure out
    #   how to accelerate, if at all.
    #
    # Another possibility is to just delete this method, rely on the code above.
    # In addition, we've implemented a simulator .. so as to remove the need for mininet/floodlight
    # completely for scale tests (scale testing of everything except floodlight)
    #
    needs_to_be_refactored = True
    if needs_to_be_refactored:
        return json.dumps({'status': 'refactor me'})

    _switch_threads=[]
    _link_threads=[]

    switch_count = request.json['switches']
    link_count = request.json['links']
    num_worker_threads = request.json.get('threads', 10)
    logger.debug("==> switch count={}; link count={};  num threads = {}".
                 format(switch_count,link_count, num_worker_threads))

    add_controllers(request.json['controllers'])

    ##############
    # ADD SWITCHES
    ##############
    names = Queue.Queue()
    for i in range(switch_count):
        name = "s" + str(i+1)
        names.put(name)

    def add_switch_worker():
        while True:
            name = names.get()
            switch = OVSKernelSwitch(name, protocols='OpenFlow13', inNamespace=False)
            switches[name] = switch
            switch.start(controllers)
            # time to here, 5 switches : 0.88s,  50sw : 2.8 (15),    200 : 22s (33)
            logger.debug("==> added switch name={}".format(name))
            names.task_done()

    if num_worker_threads > len(_switch_threads):
        logger.debug("==> Starting Switch Threads {} .. {}".
                     format(len(_switch_threads),num_worker_threads))
        for i in range(len(_switch_threads), num_worker_threads):
            t = threading.Thread(target=add_switch_worker)
            t.daemon = True
            _switch_threads.append(t)
            t.start()
    else:
        logger.debug("==> Num Switch Threads is >= num_worker_threads {},{}".
                     format(len(_switch_threads),num_worker_threads))

    names.join()       # block until all tasks are done


    #############
    # ADD LINKS #
    #############
    ep_tuples = Queue.Queue()
    # for i in range(link_count):
    #     ep1 = "s" + str(random.randint(1,switch_count));
    #     ep2 = "s" + str(random.randint(1,switch_count));
    #     ept = (ep1,ep2)  # end point tuple
    #     ep_tuples.put(ept)

    for i in range(switch_count-1):
        ep1 = "s" + str(i+1);
        ep2 = "s" + str(i+2);
        ept = (ep1,ep2)  # end point tuple
        ep_tuples.put(ept)

    lock = threading.Lock()
    switch_locks = {}
    for i in range(switch_count):
        sid = "s" + str(i+1);
        switch_locks[sid] = threading.Lock()

    def add_link_worker():
        while True:
            if ep_tuples.qsize() == 0:
                time.sleep(5)
                break

            success1 = success2 = False
            ept = None
            lock.acquire()
            logger.debug("==> ++++ lock {}".format(threading.current_thread().getName()))
            for _ in range (ep_tuples.qsize()):
                ept = ep_tuples.get()
                if ept:
                    success1 = switch_locks[ept[0]].acquire(False)
                    success2 = switch_locks[ept[1]].acquire(False)
                    logger.debug("==> switches ???? {} {} {} {}".format(ept[0],ept[1],success1, success2))
                    # if successful, process the tuple. Otherwise release the locks
                    if success1 and success2:
                        break
                    if success1: switch_locks[ept[0]].release()
                    if success2: switch_locks[ept[1]].release()
                    ep_tuples.put(ept) # put it back
                    ep_tuples.task_done()  # since put increases the count; this round is "done"

            lock.release()
            logger.debug("==> ---- lock {}".format(threading.current_thread().getName()))

            # if both are true, then we'll need to release locks at the end.
            # if either aren't true, the resources will have already been released

            if success1 and success2:
                placed = False
                try:
                    link = TCLink(switches[ept[0]], switches[ept[1]])
                    link.intf1.node.attach(link.intf1)
                    link.intf2.node.attach(link.intf2)
                    links[link_name(link)] = link
                    placed = True
                    logger.debug("==> switches ++++ {} {}".format(ept[0],ept[1]))
                except:
                    # Release the locks on the switches
                    logger.debug("==>==> ## ERROR adding link, putting back on queue={}".format(
                        ept))

                if not placed:
                    ep_tuples.put(ept)

                switch_locks[ept[0]].release()
                switch_locks[ept[1]].release()
                ep_tuples.task_done()
            time.sleep(1)

    if num_worker_threads > len(_link_threads):
        logger.debug("==> Starting Link Threads {} .. {}".
                     format(len(_link_threads),num_worker_threads))
        for i in range(len(_link_threads),num_worker_threads):
            t = threading.Thread(target=add_link_worker)
            t.daemon = True
            _link_threads.append(t)
            t.start()
    else:
        logger.debug("==> Num Link Threads is >= num_worker_threads {},{}".
            format(len(_link_threads),num_worker_threads))


    while not ep_tuples.empty():
        ep_tuples.join()       # block until all tasks are done
        logger.debug("==> LINKS: {} ".format(len(links)))
        logger.debug("==> QUEUE: {} {}".format(ep_tuples.qsize(), ep_tuples.empty()))

    response.content_type = 'application/json'
    return json.dumps({'status': 'ok'})


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
@required_parameters("switch","inport","vlan","outport","priority")
def add_transit_flow(p):
    switch = p['switch']
    inport = p['inport']
    vlan = p['vlan']
    outport = p['outport']
    priority = p['priority']

    cmd1 = "%s %s idle_timeout=0,priority=%s,in_port=%s,dl_vlan=%s,actions=output:%s" % \
           (ofctl_start, switch,priority,inport,vlan,outport)
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
