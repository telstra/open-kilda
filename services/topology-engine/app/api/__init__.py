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
        result = requests.post('http://neo4j:7474/db/data/cypher', data=data, headers=headers, auth=('neo4j', 'temppass'))
        j = json.loads(result.text)
        for n in j['data']:
            n
            for r in n:
                r['outgoing_typed_relationships']
        return str(j)
    except Exception as e:
        return "error: {}".format(str(e))

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)