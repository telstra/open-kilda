#!/usr/bin/python
import requests
from flask import Flask, render_template


print "Starting topology engine API"
app = Flask(__name__)

@app.route('/')
def index():
    try:
        headers = {'Content-Typet': 'application/json'}
        data = {'query' : 'MATCH (n) return n'}
        r = requests.post('http://neo4j:7474/db/data/cypher', data=data, headers=headers, auth=('neo4j', 'temppass'))
        return r.text
    except Exception as e:
        return "error: {}".format(str(e))

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)