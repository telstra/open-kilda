"""
get_link_props.py facilitates testing the feature through the NB API.

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

result = requests.get(url, headers=headers)

print result.status_code
print result.text


