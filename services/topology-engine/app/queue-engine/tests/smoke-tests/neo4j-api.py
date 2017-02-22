#!/usr/bin/python
import requests
import json
from pprint import pprint 

headers = {'Content-Typet': 'application/json'}
data = {'query' : 'MATCH (n) return n'}

result_switches = requests.post('http://localhost:7474/db/data/cypher', data=data, headers=headers, auth=('neo4j', 'temppass'))
j_switches = json.loads(result_switches.text)
for n in j_switches['data']:
    for r in n:
        node = {} 
        node['name'] = (r['data']['name'])
        print " "
        result_relationships = requests.get(str(r['outgoing_relationships']), auth=('neo4j', 'temppass'))
        j_paths = json.loads(result_relationships.text)
        for j_path in j_paths:
            outgoing_relationships = []
            if j_path['type'] == u'isl':
                outgoing_relationships.append(j_path['data']['dst_switch'])
                print outgoing_relationships
            node['outgoing_relationship'] = outgoing_relationships 
        pprint(node)
