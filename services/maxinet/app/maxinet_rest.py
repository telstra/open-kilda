#!/usr/bin/python

from bottle import get, post, request, run, Bottle, response, request, error

from mininet.topo import Topo
from mininet.node import OVSSwitch

from MaxiNet.Frontend import maxinet
from MaxiNet.tools import Tools

from jsonschema import validate
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
  }
}

@error(500)
def error_handler_500(error):
    return json.dumps({"message": str(error.exception)})

@post('/frontend/cluster')
def create_cluster():
    logger.debug("creating cluster with json={}"
                 .format(request.json))
    validate(request.json, cluster_schema)
    name = _create_cluster(request.json)
    response.content_type = 'application/json'
    response.status = 201
    response.set_header('Location', "http://frontend/cluster/{}".format(name))
    return json.dumps({'name': name})

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
    validate(request.json, experiment_schema)
    name = _create_experiment(request.json['name'], request.json['cluster'], request.json['topo'])
    response.content_type = 'application/json'
    response.status = 201
    response.set_header('Location', "http://frontend/experiment/{}".format(name))
    return json.dumps({'name': name})

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
