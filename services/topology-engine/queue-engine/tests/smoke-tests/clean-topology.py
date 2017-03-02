#!/usr/bin/python
import requests
import json

print "Clearing exiting topology."
headers = {'Content-Type': 'application/json'}
result_clear = requests.post('http://localhost:38080/cleanup', headers=headers)
print "Successful"