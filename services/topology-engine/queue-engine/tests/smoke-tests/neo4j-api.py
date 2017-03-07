#!/usr/bin/python
import requests
import json
from pprint import pprint 

src_switch = "00:00:00:00:00:00:00:1b"
src_port = "22"
dst_switch = "00:00:00:00:00:00:00:30"
dst_port = "33"


query = "MATCH (a:switch{{name:'{}'}}),(b:switch{{name:'{}'}}), p = shortestPath((a)-[:isl*..15]->(b)) RETURN p".format(src_switch,dst_switch)
print query
data = {'query' : query}    
result_path = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=('neo4j', 'temppass'))
j_path = json.loads(result_path.text)

path = []

path.append("{}:{}".format(src_port, src_switch))


print json.dumps(j_path, indent=4, sort_keys=True)



print path