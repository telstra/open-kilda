"""
get_links.py - get all the links

The path is NB -> TER -> NEO4J.
"""
import requests
from base64 import b64encode

url = "http://localhost:8088/api/v1/links"
headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

#
# This models one of the first flows used by ATDD. It sends the request to teh NB API so that
# kilda will construct the flow path rules.
# TODO: would be better to pull from the same data, ensure code bases on synchronized..
#       at the moment, this is hardcoded here, and ATDD has a separate source.
#

result = requests.get(url, headers=headers)

print result.status_code
print result.text


