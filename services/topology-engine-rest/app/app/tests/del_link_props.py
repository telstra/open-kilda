"""
del_link_props.py facilitates testing the feature through the NB API.

Whereas there doesn't need to be anything in the DB .. call put_link_props first.

The path is NB -> TER -> NEO4J.
"""
import requests
from base64 import b64encode

url = "http://localhost:8088/api/v1/flows"
headers = {
    'Content-Type': 'application/json',
    'correlation_id': 'deploy-flow-1',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

#
# This models one of the first flows used by ATDD. It sends the request to teh NB API so that
# kilda will construct the flow path rules.
# TODO: would be better to pull from the same data, ensure code bases on synchronized..
#       at the moment, this is hardcoded here, and ATDD has a separate source.
#

j_data = {"flowid":"c3none",
          "source":
              {"switch-id":"de:ad:be:ef:00:00:00:02", "port-id":1, "vlan-id":0},
          "destination":
              {"switch-id":"de:ad:be:ef:00:00:00:04", "port-id":2, "vlan-id":0},
          "maximum-bandwidth":10000,
          "description":"c3none",
          "last-updated":"null"}
result = requests.put(url, json=j_data, headers=headers)

print result.status_code
print result.text


