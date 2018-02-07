"""
del_link_props.py facilitates testing the feature through the NB API.

Whereas there doesn't need to be anything in the DB .. call put_link_props first.

The path is NB -> TER -> NEO4J.
"""
import requests
from base64 import b64encode

url = "http://localhost:8088/api/v1/link/props"
headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

j_data = [{"src_sw": "de:ad:be:ef:01:11:22:01",
           "src_pt": "1",
           "dst_sw": "de:ad:be:ef:02:11:22:02",
           "dst_pt": "2",
           "props": {
               "cost": "1"
               , "popularity": "5"
           }}]

result = requests.delete(url, json=j_data, headers=headers)

print result.status_code
print result.text


