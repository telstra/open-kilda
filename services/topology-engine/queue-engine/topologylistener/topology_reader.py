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
import json
import os
import time
from glob import glob

import message_utils
import logging

logger = logging.getLogger(__name__)


def read_topologies():
    logger.info('Start reading predefined topologies from json files')

    path = os.path.join(os.path.dirname(__file__), os.pardir, "topologies",
                        "*.json")
    for filename in glob(path):
        with open(filename) as infile:
            send_topology(json.load(infile))

    logger.info('All required topologies are pre-populated')


def send_topology(topology):
    for switch in topology.get('switches', {}):
        switch[u"state"] = 'CACHED'
        switch[u"message_type"] = "switch"
        logger.info('Pre-populating switch: {}'.format(switch))
        send_message(switch)

    for isl in topology.get('links', {}):
        isl[u"state"] = 'CACHED'
        isl[u"message_type"] = "isl"
        logger.info('Pre-populating isl: {}'.format(isl))
        send_message(isl)

    for flow_payload in topology.get('flows', {}):
        flow = {
            "payload": flow_payload,
            "operation": "CACHE",
            "message_type": "flow_operation"
        }
        logger.info('Pre-populating flow: {}'.format(flow))
        send_message(flow)


def send_message(data):
    logger.info(data)
    message_utils.send_cache_message(data, "topology_reader-{}".format(time.time()))
