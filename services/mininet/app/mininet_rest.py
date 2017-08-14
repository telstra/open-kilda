#!/usr/bin/python

from bottle import get, post, request, run, Bottle, response, request
from mininet.net import Mininet
from mininet.node import RemoteController, OVSKernelSwitch
from mininet.clean import cleanup
from mininet.link import TCLink
from jsonschema import validate
import logging
import json
import socket
import Queue
import threading
import random
import time


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


def add_controller(name, host, port):
    logger.debug("adding controller name={}, host={}, port={}"
                 .format(name, host, port))
    ip = socket.gethostbyname(host)
    controller = RemoteController(name, ip=ip, port=port)
    controller.start()
    controllers.append(controller)
    return controller


def add_controllers(controllers):
    for controller in controllers:
        add_controller(controller['name'],
                       controller['host'],
                       controller['port'])


def list_controllers():
    data = []
    for controller in controllers:
        data.append({"name": controller.name,
                     "host": controller.ip,
                     "port": controller.port})
    return data


def add_switches(switches):
    for switch in switches:
        add_switch(switch['name'],switch['dpid'],
                   switch.get('protocol_version', 'OpenFlow13'))


def add_switch(name, dpid, proto_version):
    if type(dpid) is unicode:
        dpid = dpid.encode('ascii','ignore')

    switch = OVSKernelSwitch(name, protocols=proto_version, inNamespace=False, dpid=dpid)
    switch.start(controllers)
    switches[name] = switch
    logger.debug("==> added switch name={}; dpid={}".format(name,dpid))


def list_switch(name):
    switch = switches[name]
    intfs = []
    if len(switch.intfs) > 0:
        for i in switch.intfs:
            intf = switch.intfs[i]
            intfs.append({'name': intf.name,
                          'mac': intf.mac,
                          'status': intf.status()})
    return {'name': name,
            'dpid': switch.dpid,
            'connected': switch.connected(),
            'interface': intfs}


def list_switches():
    data = []
    for name, switch in switches.iteritems():
        data.append(list_switch(name))
    return data


def link_name(link):
    name = "{}:{}".format(link.intf1.name, link.intf2.name)
    if link.intf1.name < link.intf2.name:
        name = "{}:{}".format(link.intf2.name, link.intf1.name)
    return name


def add_links(links):
    for link in links:
        add_link(link['node1'], link['node2'])


def add_link(node1, node2):
    link = TCLink(switches[node1], switches[node2])
    link.intf1.node.attach(link.intf1)
    link.intf2.node.attach(link.intf2)
    links[link_name(link)] = link


def list_links():
    data = []
    for name, link in links.iteritems():
        data.append({'name': name, 'status': link.status()})
    return {"links": data}


@post('/topology')
def create_topology():
    validate(request.json, topology_schema)
    add_controllers(request.json['controllers'])
    add_switches(request.json['switches'])
    add_links(request.json['links'])
    response.content_type = 'application/json'
    return json.dumps({'controllers': list_controllers(),
                       'switches': list_switches(),
                       'links': list_links()})


#
# Create a linear topology with random links between the switches.
# - create N switches
# - create a random set of M links amongst the switches
#
@post('/create_random_linear_topology')
def create_topology():
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
    return json.dumps(list_links())


@post('/links')
def create_links():
    validate(request.json, links_schema)
    add_links(request.json['links'])
    response.content_type = 'application/json'
    return json.dumps(list_links())


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
    del controllers[:]
    switches.clear()
    links.clear()
    cleanup()
    return {'status': 'ok'}


@get('/status')
def status():
    return {'status': 'ok'}


def start_server(interface, port):
    run(host='0.0.0.0', port=port, debug=True)


def main():
    global logger
    global controllers
    global switches
    global links
    switches = {}
    links = {}
    controllers = []
    logging.basicConfig(format='%(levelname)s:%(message)s',
                        level=logging.DEBUG)
    logger = logging.getLogger()
    mininet_cleanup()
    start_server('0.0.0.0', 38080)

if __name__ == '__main__':
    main()
