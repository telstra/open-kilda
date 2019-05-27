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

import os
import traceback
import logging

import requests
from service.topology import Topology
from service.lockkeeper import init_app
from common import init_logger, run_process, loop_forever

init_logger()
logger = logging.getLogger()

LAB_ID = os.environ.get("LAB_ID", 1)
API_HOST = os.environ.get("API_HOST", 'lab-api.pendev:8288')


def main():
    base_url = "http://{}/api/{}/".format(API_HOST, LAB_ID)
    definition_url = base_url + 'definition'
    activate_url = base_url + 'activate'

    try:
        topo_def = requests.get(definition_url).json()
        topo = Topology.create(topo_def)

        logger.info("Running topology")
        topo.run()

        logger.info("Running rest server")
        lockkeeper_app = init_app(topo.switches)
        lockkeeper_proc = run_process(lambda: lockkeeper_app.run('0.0.0.0', 5001, threaded=True))
    except Exception as ex:
        requests.post(activate_url, json={'error': traceback.format_exc()})
        raise ex

    requests.post(activate_url, json={})

    def teardown():
        logger.info("Terminating...")
        topo.destroy()
        lockkeeper_proc.terminate()
        lockkeeper_proc.join()
    loop_forever(teardown)
