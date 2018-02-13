#!/usr/bin/env python

"""
push_flows.py facilitates testing the feature through the NB API.

This will create some Switches if they don't exist, flows between them, and possibly isls with the
available_bandwidth adjusted (possibly negative since I won't know what it is).

The path will hit 2 places:
    (1) NB -> TER -> NEO4J
    (2) NB -> kafka -> FLOW TOPOLOGY

"""
import requests
from base64 import b64encode

url = "http://localhost:80/api/v1/push/flows"

headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

j_data = [{
    "flow_id": "fat-flow-1",
    "src_node": "de:ad:be:ef:01:11:22:01-p.3-v.45",
    "dst_node": "de:ad:be:ef:01:11:22:01-p.1-v.23",
    "max_bandwidth": 1000,
    "forward_path": [{
        "switch_name": "my.big.switch",
        "switch_id": "de:ad:be:ef:01:11:22:01",
        "cookie": "0x10400000005dc8f",
        "cookie_int": 73183493945154703
    }],
    "reverse_path": [{
        "switch_name": "my.big.switch",
        "switch_id": "de:ad:be:ef:01:11:22:01",
        "cookie": "0x18400000005dc8f",
        "cookie_int": 109212290964118671
    }]
}]


result = requests.put(url, json=j_data, headers=headers)

print(result.status_code)
print(result.text)

