#!/usr/bin/python
import requests
import json
from flask import Flask, render_template


print "Starting topology engine API"
app = Flask(__name__)

@app.route('/')
def index():
    try:
        headers = {'Content-Typet': 'application/json'}
        data = {'query' : 'MATCH (n) return n'}

        result_switches = requests.post('http://neo4j:7474/db/data/cypher', data=data, headers=headers, auth=('neo4j', 'temppass'))
        j_switches = json.loads(result_switches.text)
        topology = []
        for n in j_switches['data']:
            for r in n:
                node = {} 
                node['name'] = (r['data']['name'])
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=('neo4j', 'temppass'))
                j_paths = json.loads(result_relationships.text)
                outgoing_relationships = []
                for j_path in j_paths:
                    if j_path['type'] == u'isl':
                        outgoing_relationships.append(j_path['data']['dst_switch'])
                    node['outgoing_relationships'] = outgoing_relationships
            topology.append(node)
        return str(json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True, indent=4))
    except Exception as e:
        return "error: {}".format(str(e))

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)