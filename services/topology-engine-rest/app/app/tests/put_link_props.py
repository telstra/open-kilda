"""
put_link_props.py facilitates testing the feature through the NB API.

This will create a few link_props.

The path is NB -> TER -> NEO4J.
"""
import requests
from base64 import b64encode

# url = "http://localhost:8088/api/v1/link/props"
url = "http://northbound.polaris.pn.telstra.com:8080/api/v1/link/props"

headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

j_data = [{"src_switch": "de:ad:be:ef:01:11:22:01",
          "src_port": "1",
          "dst_switch": "de:ad:be:ef:02:11:22:02",
          "dst_port": "2",
          "props": {
                "cost": "1"
                , "popularity": "5"
          }}]

result = requests.put(url, json=j_data, headers=headers)

print result.status_code
print result.text

