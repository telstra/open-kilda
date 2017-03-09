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
        add_switch(switch['name'],switch['dpid'])


def add_switch(name,dpid):
    if type(dpid) is unicode:
        dpid = dpid.encode('ascii','ignore')

    switch = OVSKernelSwitch(name, protocols='OpenFlow13', inNamespace=False, dpid=dpid)
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
