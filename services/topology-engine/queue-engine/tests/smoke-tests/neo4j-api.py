#!/usr/bin/python
import requests
import json
from pprint import pprint 

src_switch = "00:00:00:00:00:00:00:01"
src_port = "22"
dst_switch = "00:00:00:00:00:00:00:04"
dst_port = "33"


def get_reciprocal_relationship(relationshipURL):
    reciprocalRelationship = {}
    resultRelationship = requests.get(relationshipURL, auth=('neo4j', 'temppass'))
    jResultRelationship = json.loads(resultRelationship.text)
    reciprocalRelationship['src_port'] = jResultRelationship['data']['dst_port']
    reciprocalRelationship['src_switch'] = jResultRelationship['data']['dst_switch']
    reciprocalRelationship['dst_port'] = jResultRelationship['data']['src_port']
    reciprocalRelationship['dst_switch'] = jResultRelationship['data']['src_switch']
    query = "MATCH p=()-[r:isl{{ src_switch: '{0}',src_port: '{1}', dst_switch: '{2}', dst_port: '{3}'  }}]->() RETURN p".format(reciprocalRelationship['src_switch'], reciprocalRelationship['src_port'], reciprocalRelationship['dst_switch'], reciprocalRelationship['dst_port'])
    data = {'query' : query} 
    resultReciprocalRelationship = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
    jResultReciprocalRelationship = json.loads(resultReciprocalRelationship.text)
    return jResultReciprocalRelationship['data'][0][0]['relationships'][0]

query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..15]->(b)) RETURN p".format(src_switch,dst_switch)
data = {'query' : query}    
resultForwardPath = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
jResultForwardPath = json.loads(resultForwardPath.text)

query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..15]->(b)) RETURN p".format(dst_switch,src_switch)
data = {'query' : query}    
resultReturnPath = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
jResultReturnPath = json.loads(resultReturnPath.text)

print json.dumps(jResultReturnPath, indent=4, sort_keys=True)
print json.dumps(jResultForwardPath, indent=4, sort_keys=True)