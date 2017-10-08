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

from time import time

# from kafka import KafkaConsumer, KafkaProducer
import requests
import json
import pprint


def create_topo(file):
    print "\nCreating new topology."
    with open(file) as infile:
        j_data = json.load(infile)

    headers = {'Content-Type': 'application/json'}
    start = time()
    result = requests.post('http://localhost:38080/topology', json=j_data, headers=headers)
    print "==> Time: ", time()-start
    if result.status_code == 200:
        print "==> Successful"
    else:
        print "==> Failure:", result.status_code
        print result.text

