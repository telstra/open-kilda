#!/usr/bin/python

import requests
import json

data = '{"statements":[{"statement":"MATCH (n) RETURN n", "resultDataContents":["graph"]}]}'
headers = {'Content-type': 'application/json'} 
auth = ('neo4j', 'temppass')
result_switches = requests.post('http://localhost:7474/db/data/transaction/commit', data=data, auth=auth, headers=headers)
json_network = json.loads(result_switches.text)
print json_network