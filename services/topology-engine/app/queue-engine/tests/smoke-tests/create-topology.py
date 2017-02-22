#!/usr/bin/python
from kafka import KafkaConsumer, KafkaProducer
import requests
import json

print "Clearing exiting topology."
headers = {'Content-Type': 'application/json'}
result_clear = requests.post('http://localhost:38080/cleanup', headers=headers)
print "Successful"

print "Creating new topology."
data = {'controllers':[{'name': 'floodlight','host': 'kilda','port': 6653}],'links': [{'node1': 'sw1','node2': 'sw2'}],'switches': [{'name': 'sw1','dpid': '0000000000000001'},{'name': 'sw2','dpid': '0000000000000002'}]}
j_data = json.dumps(data)
result_switches = requests.post('http://localhost:38080/topology', data=j_data, headers=headers)
print "Successful"


