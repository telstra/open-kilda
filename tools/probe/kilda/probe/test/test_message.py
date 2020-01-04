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

from kilda.probe.entity.message import create_dump_state


def test_validate_message_request_format():
    with open('./kilda/probe/test/res/CtrlRequest.json') as f:
        etalon_request = json.load(f)

    dump_state_command = create_dump_state(etalon_request['correlation_id'],
                                           etalon_request['route'])

    dump_state_command._timestamp = etalon_request['timestamp']

    dump_state_command_dict = json.loads(
        dump_state_command.serialize().decode("utf-8"))

    assert etalon_request == dump_state_command_dict, \
        'CtrlRequest.json format invalid'
