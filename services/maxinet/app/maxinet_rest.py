#!/usr/bin/python

from bottle import get, post, request, run, Bottle, response, request, error, abort

from mininet.topo import Topo
from mininet.node import OVSSwitch

from MaxiNet.Frontend import maxinet
from MaxiNet.tools import Tools

from jsonschema import validate, ValidationError
import logging
import json
import socket

cluster_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
      "name": {
        "type": "string"
      },
      "minWorkers": {
        "type": "integer"
      },
      "maxWorkers": {
        "type": "integer"
      }
  },
  "required": [
    "name",
    "minWorkers",
    "maxWorkers"
  ]
}

experiment_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "name": {
      "type": "string"
    },
    "cluster": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string"
        },
      },
    },
    "topo": {
      "type": "object",
      "properties": {
        "hosts": {
          "type": "array",
            "items": {
              "type": "object",
                "properties": {
                  "name": {
                    "type": "string"
                  },
                },
            },
         },
        "switches": {
          "type": "array",
            "items": {
              "type": "object",
                "properties": {
                  "name": {
                    "type": "string"
                  },
                },
            },
         },
        "links": {
          "type": "array",
            "items": {
              "type": "object",
                "properties": {
                  "name": {
                    "type": "string"
                  },
                },
            },
         },
      },
    },
  },
  "required": [
    "name",
    "cluster",
    "topo"
  ]
}

host_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
      "name": {
        "type": "string"
      },
      "pos": {
        "type": "string"
      },
  },
  "required": [
    "name",
    "pos"
  ]
}

switch_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
      "name": {
        "type": "string"
      },
      "workerId": {
        "type": "integer"
      }
  },
  "required": [
    "name",
    "workerId"
  ]
}

link_schema = {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
      "node1": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "type": {
            "type": "string"
          },
        },
      },
      "node2": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "type": {
            "type": "string"
          },
        },
      }
  },
  "required": [
    "node1",
    "node2"
  ]
}

@error(500)
def error_handler_500(error):
    return json.dumps({"message": str(error.exception)})

@error(400)
def error_handler_400(error):
    return json.dumps({"message": str(error.exception)})

@post('/frontend/cluster')
def create_cluster():
    try:
        logger.debug("creating cluster with json={}"
                 .format(request.json))
        validate(request.json, cluster_schema)
        name = _create_cluster(request.json)
        response.content_type = 'application/json'
        response.status = 201
        response.set_header('Location', "http://frontend/cluster/{}".format(name))
        return json.dumps({'name': name})
    except ValidationError as e:
        abort(400, e.message)

def _create_cluster(cluster):
    name = cluster['name']
    minWorkers = cluster['minWorkers']
    maxWorkers = cluster['maxWorkers']
    logger.debug("creating cluster with minWorkers={}, maxWorkers={}"
                 .format(minWorkers, maxWorkers))
    c = maxinet.Cluster(minWorkers = minWorkers, maxWorkers = maxWorkers)
    clusters[name] = c
    return name

def create_topo(topo):
    t = Topo()

    i = 1
    for host in topo['hosts']:
        logger.debug("add host {} to topo" .format(host['name']))
        t.addHost(host['name'], ip=Tools.makeIP(i), mac=Tools.makeMAC(i))
        i += 1

    i = 1
    for switch in topo['switches']:
        logger.debug("add switch {} to topo" .format(switch['name']))
        t.addSwitch(switch['name'], dpid=Tools.makeDPID(i))
        i += 1

    i = 1
    for link in topo['links']:
        logger.debug("add link from {} to {} to topo" .format(link['node1']['name'], link['node2']['name']))
        t.addLink(link['node1']['name'], link['node2']['name'])
        i += 1

    return t


@post('/frontend/experiment')
def create_experiment():
    try:
        validate(request.json, experiment_schema)
        name = _create_experiment(request.json['name'], request.json['cluster'], request.json['topo'])
        response.content_type = 'application/json'
        response.status = 201
        response.set_header('Location', "http://frontend/experiment/{}".format(name))
        return json.dumps({'name': name})
    except ValidationError as e:
        abort(400, e.message)

def _create_experiment(name, cluster, topo):
    logger.debug("creating experiment with name={}, cluster={}"
                 .format(name, cluster['name']))
    c = clusters[cluster['name']]
    t = create_topo(topo)
    experiment = maxinet.Experiment(c, t, switch=OVSSwitch)
    experiments[name] = experiment
    return name

@get('/experiment/setup/<name>')
def setup_experiment(name):
    logger.debug("setup experiment with name={}" .format(name))
    experiment = experiments[name]
    experiment.setup()
    response.content_type = 'application/json'
    return json.dumps({'name': name})

@get('/experiment/stop/<name>')
def stop_experiment(name):
    logger.debug("stop experiment with name={}" .format(name))
    experiment = experiments[name]
    experiment.stop()
    response.content_type = 'application/json'
    return json.dumps({'name': name})

@get('/experiment/run/<name>/<node>/<command>')
def setup_experiment(name, node, command):
    logger.debug("run command experiment={}, node={}, command={}" .format(name, node, command))
    experiment = experiments[name]
    output = experiment.get_node(node).cmd(command)
    response.content_type = 'application/json'
    return json.dumps({'name': name, 'node': node, 'command': command, 'output': output})

@post('/experiment/<experiment>/host')
def create_host(experiment):
    try:
        validate(request.json, host_schema)
        host = _create_host(experiment, request.json)
        response.content_type = 'application/json'
        response.status = 201
        response.set_header('Location', "http://experiment/{}/host/{}".format(experiment, host.name))
        return json.dumps({'name': host.name, 'type': 'HOST'})
    except ValidationError as e:
        abort(400, e.message)

def _create_host(experiment, host):
    logger.debug("creating host for experiment={}, host={}, pos={}"
                 .format(experiment, host['name'], host['pos']))
    exp = experiments[experiment]
    host_id = len(exp.hosts) + 1
    hst = exp.addHost(host['name'], ip=Tools.makeIP(host_id), max=Tools.makeMAC(host_id), pos=host['pos'])
    return hst

@post('/experiment/<experiment>/switch')
def create_switch(experiment):
    try:
        validate(request.json, switch_schema)
        switch = _create_switch(experiment, request.json)
        response.content_type = 'application/json'
        response.status = 201
        response.set_header('Location', "http://experiment/{}/switch/{}".format(experiment, switch.name))
        return json.dumps({'name': switch.name, 'type': 'SWITCH'})
    except ValidationError as e:
        abort(400, e.message)

def _create_switch(experiment, switch):
    exp = experiments[experiment]
    switch_id = len(exp.switches) + 1

    logger.debug("creating switch for experiment={}, switch={}, workerId={}, dpid={}"
                 .format(experiment, switch['name'], switch['workerId'], switch_id))
    sw = exp.addSwitch(switch['name'], dpid=Tools.makeDPID(switch_id), wid=switch['workerId'])
    return sw

@post('/experiment/<experiment>/link')
def create_link(experiment):
    try:
        validate(request.json, link_schema)
        link = _create_link(experiment, request.json['node1'], request.json['node2'])
        response.content_type = 'application/json'
        response.status = 201

        # TODO - how to get info from the returned "link" object?
        response.set_header('Location', "http://experiment/{}/link/{}/{}".format(experiment, request.json['node1']['name'], request.json['node2']['name']))
        return json.dumps({'node1': {'name': request.json['node1']['name'], 'type': request.json['node1']['type']}, 'node2': {'name': request.json['node2']['name'], 'type': request.json['node2']['type']}})
    except ValidationError as e:
        abort(400, e.message)

def _create_link(experiment, node1, node2):
    exp = experiments[experiment]

    logger.debug("creating link for experiment={}, node1={}, node1type={}, node2={}, node2type={}"
                 .format(experiment, node1['name'], node1['type'], node2['name'], node2['type']))
    link = exp.addLink(node1['name'], node2['name'], autoconf=True)
    return link

def start_server(interface, port):
    run(host='0.0.0.0', port=port, debug=True)


def main():
    global logger
    global clusters
    global experiments
    clusters = {}
    experiments = {}

    logging.basicConfig(format='%(levelname)s:%(message)s',
                        level=logging.DEBUG)
    logger = logging.getLogger()
    #mininet_cleanup()
    start_server('0.0.0.0', 38081)

if __name__ == '__main__':
    main()
