#!/usr/bin/python
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

import sys
import json
from time import sleep
from logging.config import dictConfig


import api.api as api
import service.service as service
from service.cmd import run_cmd

with open("./log.json", "r") as fd:
    dictConfig(json.load(fd))

mode = sys.argv[1]
if mode == 'api':
    api.main()
else:
    run_cmd('ovs-ctl start')
    run_cmd('ovs-vsctl init')
    sleep(1)    # delay for applying connection with api container network
    service.main()
