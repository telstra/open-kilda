#!/usr/bin/python
import requests
import json
import pprint

url = "http://localhost/api/v1/flow"
headers = {'Content-Type': 'application/json'}
j_data = {"src_switch":"00:00:00:00:00:00:00:01", "src_port":4, "src_vlan":111, "dst_switch":"00:00:00:00:00:00:00:02", "dst_port":4, "dst_vlan":555}
result = requests.post(url, json=j_data, headers=headers)
print result.text

j_data = {"dst_switch":"00:00:00:00:00:00:00:01", "dst_port":4, "dst_vlan":111, "src_switch":"00:00:00:00:00:00:00:02", "src_port":4, "src_vlan":555}
result = requests.post(url, json=j_data, headers=headers)

