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
import pprint

#For the following mn topo
#mn --controller=remote,ip=172.18.0.1,port=6653 --switch ovsk,protocols=OpenFlow13 --topo torus,3,3
#h1x1 ping h3x2

url = "http://localhost/api/v1/flow"
headers = {'Content-Type': 'application/json'}
j_data = {"src_switch":"00:00:00:00:00:00:01:01", "src_port":1, "src_vlan":0, "dst_switch":"00:00:00:00:00:00:03:02", "dst_port":1, "dst_vlan":0, "bandwidth": 2000}
result = requests.post(url, json=j_data, headers=headers)
print result.text
