# Copyright 2018 Telstra Open Source
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

import json
from service.topology import Topology


def test_parse_smoke(mocker):
    mocker.patch('service.topology.run_cmd')
    mocker.patch('service.topology.vsctl')
    mocker.patch('service.topology.ofctl')

    with open("./service/test/res/topology.json", "r") as f:
        Topology.create(json.loads(f.read()))
