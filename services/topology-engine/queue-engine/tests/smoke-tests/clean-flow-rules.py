#!/usr/bin/env python
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
from base64 import b64encode

url = "http://localhost:8088/api/v1/flows/c3none"
headers = {
    'Content-Type': 'application/json',
    'correlation_id': 'delete-flow-1',
    'Authorization': 'Basic %s' % b64encode(b"kilda:kilda").decode("ascii")
}

#
# This models one of the first flows used by ATDD.
# TODO: would be better to pull from the same data, ensure code bases on synchronized..
#       at the moment, this is hardcoded here, and ATDD has a separate source.
#

result = requests.delete(url, headers=headers)

print result.status_code
print result.text

