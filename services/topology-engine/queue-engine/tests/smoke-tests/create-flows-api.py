#!/usr/bin/python
import requests
import json
import pprint

#For the following mn topo
#mn --controller=remote,ip=172.18.0.1,port=6653 --switch ovsk,protocols=OpenFlow13 --topo torus,3,3
#h1x3 ping h2x1

url = "http://localhost/api/v1/flow"
headers = {'Content-Type': 'application/json'}
j_data = {"src_switch":"00:00:00:00:00:00:01:01", "src_port":1, "src_vlan":0, "dst_switch":"00:00:00:00:00:00:01:02", "dst_port":1, "dst_vlan":0, "bandwidth": 2000}
result = requests.post(url, json=j_data, headers=headers)
print result.text
