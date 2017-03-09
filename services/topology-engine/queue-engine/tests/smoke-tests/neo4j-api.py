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
resultPath = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
jForwardPath = json.loads(resultPath.text)


forwardPath = []
reversePath = []

for relationshipURL in jForwardPath['data'][0][0]['relationships']:
    forwardPath.append(relationshipURL)
    reversePath.append(get_reciprocal_relationship(relationshipURL))

if len(forwardPath) == len(reversePath):
    pathLength = len(forwardPath)

i = 0

print forwardPath
print reversePath


forwardFlows = []
reverseFlows = []

while i <= pathLength:
    forwardFlow = {}
    reverseFlow = {}
    if i == 0:
        resultForwardRelationship = requests.get(forwardPath[i], auth=('neo4j', 'temppass'))
        jResultForwardRelationship = json.loads(resultForwardRelationship.text)
        resultReverseRelationship = requests.get(reversePath[i], auth=('neo4j', 'temppass'))
        jResultReverseRelationship = json.loads(resultReverseRelationship.text)
        forwardFlow['switch'] = src_switch
        forwardFlow['match'] = "in_port: {}".format(src_port)
        forwardFlow['action'] = "output: {}".format(jResultForwardRelationship['data']['src_port'])
        reverseFlow['switch'] = src_switch
        reverseFlow['match'] = "in_port: {}".format(jResultReverseRelationship['data']['dst_port'])
        reverseFlow['action'] = "output: {}".format(src_port)
    elif i == pathLength:
        print i
        resultForwardRelationship = requests.get(forwardPath[i-1], auth=('neo4j', 'temppass'))
        jResultForwardRelationship = json.loads(resultForwardRelationship.text)
        resultReverseRelationship = requests.get(reversePath[i-1], auth=('neo4j', 'temppass'))
        jResultReverseRelationship = json.loads(resultReverseRelationship.text)
        forwardFlow['switch'] = dst_switch
        forwardFlow['match'] = "in_port: {}".format(jResultForwardRelationship['data']['dst_port']) #needs and update
        forwardFlow['action'] = "output: {}".format(dst_port) 
        reverseFlow['switch'] = dst_switch
        reverseFlow['match'] = "in_port: {}".format(dst_port)
        reverseFlow['action'] = "output: {}".format(jResultReverseRelationship['data']['src_port']) #needs and update
    else:
        resultForwardRelationshipMatch = requests.get(forwardPath[i-1], auth=('neo4j', 'temppass'))
        jResultForwardRelationshipMatch = json.loads(resultForwardRelationshipMatch.text)
        resultForwardRelationshipAction = requests.get(forwardPath[i], auth=('neo4j', 'temppass'))
        jResultForwardRelationshipAction = json.loads(resultForwardRelationshipAction.text)
        resultReverseRelationshipMatch = requests.get(reversePath[i], auth=('neo4j', 'temppass'))
        jResultReverseRelationshipMatch = json.loads(resultReverseRelationshipMatch.text)
        resultReverseRelationshipAction = requests.get(reversePath[i-1], auth=('neo4j', 'temppass'))
        jResultReverseRelationshipAction = json.loads(resultReverseRelationshipAction.text)
        forwardFlow['switch'] = jResultForwardRelationshipMatch['data']['dst_switch']
        forwardFlow['match'] = "in_port: {}".format(jResultForwardRelationshipMatch['data']['dst_port'])
        forwardFlow['action'] = "output: {}".format(jResultForwardRelationshipAction['data']['src_port'])
        reverseFlow['switch'] = jResultReverseRelationshipAction['data']['src_switch']
        reverseFlow['match'] = "in_port: {}".format(jResultReverseRelationshipMatch['data']['dst_port'])
        reverseFlow['action'] = "output: {}".format(jResultReverseRelationshipAction['data']['src_port'])
    forwardFlows.append(forwardFlow)
    reverseFlows.append(reverseFlow)
    i += 1
flows = [forwardFlows, reverseFlows]
print json.dumps(flows, indent=4, sort_keys=True)



