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

import time
import json


class Message(object):
    def __init__(self, type_, destination, payload, correlation_id=None):
        self._payload = payload
        self._type = type_
        self._destination = destination
        self._timestamp = int(round(time.time() * 1000))
        self._correlation_id = correlation_id

    def serialize(self):
        value = {
            'payload': self._payload,
            'type': self._type,
            'destination': 'WFM_CTRL',
            'timestamp': self._timestamp,
            'correlation_id': self._correlation_id,
            'route': self._destination
        }

        return bytes(json.dumps(value).encode('utf-8'))


class CtrlCommandMessage(Message):
    def __init__(self, destination, correlation_id, action):
        payload = {
            "action": action
        }

        super(CtrlCommandMessage, self).__init__('CTRL_REQUEST', destination,
                                                 payload, correlation_id)


def create_list(correlation_id):
    return CtrlCommandMessage('*', correlation_id, 'list')


def create_dump_state(correlation_id, destination):
    return CtrlCommandMessage(destination, correlation_id, 'dump')
