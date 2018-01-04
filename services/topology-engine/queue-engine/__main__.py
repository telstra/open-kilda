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

import gevent.monkey

gevent.monkey.patch_all(Event=True)

import logging
import json
from logging.config import dictConfig
with open("log.json", "r") as fd:
    dictConfig(json.load(fd))

from topologylistener import eventhandler
from topologylistener.topology_reader import read_topologies

logger = logging.getLogger(__name__)

try:
    # (crimi) - commenting out until it is clear how to best leverage pre-populating topologies
    # read_topologies()

    logger.info('Topology engine starting.')
    eventhandler.main_loop()

except Exception as e:
    logger.exception("Error in main loop")
