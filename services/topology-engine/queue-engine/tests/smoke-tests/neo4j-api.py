#!/usr/bin/python
import requests
import json
import pprint

url = "http://localhost/api/v1/flow"
headers = {'Content-Type': 'application/json'}
j_data = {"src_switch":"00:00:00:00:00:00:00:01", "src_port":1, "src_vlan":0, "dst_switch":"00:00:00:00:00:00:00:07", "dst_port":1, "dst_vlan":0}
result = requests.post(url, json=j_data, headers=headers)
print result.text


