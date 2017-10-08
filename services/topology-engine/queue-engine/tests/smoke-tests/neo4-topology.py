#!/usr/bin/python
# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#


import requests
import json

data = '{"statements":[{"statement":"MATCH (n) RETURN n", "resultDataContents":["graph"]}]}'
headers = {'Content-type': 'application/json'}
auth = ('neo4j', 'temppass')
result_switches = requests.post('http://localhost:7474/db/data/transaction/commit', data=data, auth=auth, headers=headers)
json_network = json.loads(result_switches.text)
print json_network
