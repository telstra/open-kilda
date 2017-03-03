#!/usr/bin/python
from kafka import KafkaConsumer, KafkaProducer
import requests
import json

def cleanup():
    print "Clearing exiting topology."
    headers = {'Content-Type': 'application/json'}
    result_clear = requests.post('http://localhost:38080/cleanup', headers=headers)
    print "Successful"


def create_topo():
    print "Creating new topology."
    with open('full-topology.json') as infile:
        j_data = json.load(infile)

    result_switches = requests.post('http://localhost:38080/topology', data=j_data, headers=headers)
    print result_switches.text
    print "Successful"

cleanup()
create_topo()