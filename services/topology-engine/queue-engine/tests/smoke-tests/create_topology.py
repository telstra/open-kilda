#!/usr/bin/python
from time import time

# from kafka import KafkaConsumer, KafkaProducer
import requests
import json
import pprint

headers = {'Content-Type': 'application/json'}

def cleanup():
    print "\nClearing exiting topology."
    start = time()
    result = requests.post('http://localhost:38080/cleanup', headers=headers)
    print "==> Time: ", time()-start
    print "==> Successful", result


def create_topo(file):
    print "\nCreating new topology."
    with open(file) as infile:
        j_data = json.load(infile)

    start = time()
    result = requests.post('http://localhost:38080/topology', json=j_data, headers=headers)
    print "==> Time: ", time()-start
    if result.status_code == 200:
        print "==> Successful"
    else:
        print "==> Failure:", result.status_code
        print result.text

