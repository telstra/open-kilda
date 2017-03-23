#!/usr/bin/python
import requests
import json
from pprint import pprint 

src_switch = "00:00:00:00:00:00:00:01"
src_port = "11"
dst_switch = "00:00:00:00:00:00:00:05"
dst_port = "55"

class Flow:
    def __init__(self, switch, match, action):
        self.switch = switch
        self.match = "in_port: {}".format(match)
        self.action = "output: {}".format(action)

    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, 
            sort_keys=False, indent=4)

def build_ingress_flow(expandedRelationships, src_switch, src_port):
    match = src_port
    for relationship in expandedRelationships:
        if relationship['data']['src_switch'] == src_switch:
            action = relationship['data']['src_port']
    flow = Flow(src_switch, match, action)
    return flow

def build_egress_flow(expandedRelationships, dst_switch, dst_port):
    action = dst_port
    for relationship in expandedRelationships:
        if relationship['data']['dst_switch'] == dst_switch:
            match = relationship['data']['src_port']
    flow = Flow(dst_switch, match, action)
    return flow

def build_intermediate_flows(expandedRelationships, i):
    match = expandedRelationships[i]['data']['dst_port']
    action = expandedRelationships[i+1]['data']['src_port']
    switch = expandedRelationships[i]['data']['dst_switch']
    flow = Flow(switch, match, action)
    return flow

def expand_relationships(relationships):
    fullRelationships = []
    for relationship in relationships:
        fullRelationships.append(json.loads((requests.get(relationship, auth=('neo4j', 'temppass'))).text))
    return fullRelationships

def get_relationships(src_switch, src_port, dst_switch, dst_port):
    query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..100]->(b)) RETURN p".format(src_switch,dst_switch)
    data = {'query' : query}    
    resultPath = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
    jPath = json.loads(resultPath.text)
    return jPath['data'][0][0]['relationships']

def api_v1_topology_get_path(src_switch, src_port, dst_switch, dst_port):
    relationships = get_relationships(src_switch, src_port, dst_switch, dst_port)
    expandedRelationships = expand_relationships(relationships)
    flows = []
    flows.append(build_ingress_flow(expandedRelationships, src_switch, src_port))

    intermediateFlowCount = len(expandedRelationships) - 1
    i = 0

    while i < intermediateFlowCount:
        flows.append(build_intermediate_flows(expandedRelationships, i))
        i += 1

    flows.append(build_egress_flow(expandedRelationships, dst_switch, dst_port))

    return flows


forwardFlows = api_v1_topology_get_path(src_switch, src_port, dst_switch, dst_port)
reverseFlows = api_v1_topology_get_path(dst_switch, dst_port, src_switch, src_port)


flows = [forwardFlows, reverseFlows]

print flows

'''
[
    {
        "data": {
            "dst_port": 1, 
            "dst_switch": "00:00:00:00:00:00:00:02", 
            "src_port": 1, 
            "src_switch": "00:00:00:00:00:00:00:01"
        }, 
        "end": "http://localhost:7474/db/data/node/0", 
        "extensions": {}, 
        "metadata": {
            "id": 0, 
            "type": "isl"
        }, 
        "properties": "http://localhost:7474/db/data/relationship/0/properties", 
        "property": "http://localhost:7474/db/data/relationship/0/properties/{key}", 
        "self": "http://localhost:7474/db/data/relationship/0", 
        "start": "http://localhost:7474/db/data/node/1", 
        "type": "isl"
    }, 
    {
        "data": {
            "dst_port": 1, 
            "dst_switch": "00:00:00:00:00:00:00:03", 
            "src_port": 2, 
            "src_switch": "00:00:00:00:00:00:00:02"
        }, 
        "end": "http://localhost:7474/db/data/node/2", 
        "extensions": {}, 
        "metadata": {
            "id": 1, 
            "type": "isl"
        }, 
        "properties": "http://localhost:7474/db/data/relationship/1/properties", 
        "property": "http://localhost:7474/db/data/relationship/1/properties/{key}", 
        "self": "http://localhost:7474/db/data/relationship/1", 
        "start": "http://localhost:7474/db/data/node/0", 
        "type": "isl"
    }
]
'''