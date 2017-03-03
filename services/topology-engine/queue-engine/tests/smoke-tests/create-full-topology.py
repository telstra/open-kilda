#!/usr/bin/python
from kafka import KafkaConsumer, KafkaProducer
import requests
import json
import pprint

headers = {'Content-Type': 'application/json'}

def cleanup():
    print "Clearing exiting topology."
    result = requests.post('http://localhost:38080/cleanup', headers=headers)
    print "Successful", result


def create_topo():
    print "Creating new topology."
    with open('full-topology.json') as infile:
        j_data = json.load(infile)

    result = requests.post('http://localhost:38080/topology', json=j_data, headers=headers)
    if result.status_code == 200:
        print "Successful"
        print
    else:
        print "Failure:", result.status_code
        print result.text

cleanup()
create_topo()