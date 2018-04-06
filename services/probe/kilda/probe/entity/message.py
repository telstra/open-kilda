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

    JAVA_CTRL_REQUEST = 'org.openkilda.messaging.ctrl.CtrlRequest'

    def __init__(self, destination, payload, correlation_id=None,
                 _clazz=JAVA_CTRL_REQUEST):
        self._payload = payload
        self._destination = destination
        self._timestamp = int(round(time.time() * 1000))
        self._correlation_id = correlation_id
        self._clazz = _clazz

    def serialize(self):
        value = {
            'payload': self._payload,
            'destination': 'WFM_CTRL',
            'timestamp': self._timestamp,
            'correlation_id': self._correlation_id,
            'route': self._destination,
            'clazz': self._clazz
        }

        return bytes(json.dumps(value).encode('utf-8'))


class CtrlCommandMessage(Message):

    JAVA_REQUEST_DATA = 'org.openkilda.messaging.ctrl.RequestData'

    def __init__(self, destination, correlation_id, action):
        payload = {
            'clazz': self.JAVA_REQUEST_DATA,
            'action': action
        }

        super(CtrlCommandMessage, self).__init__(destination,
                                                 payload, correlation_id)


class DumpBySwitchCtrlCommandMessage(Message):

    JAVA_REQUEST_DATA = \
        'org.openkilda.messaging.ctrl.DumpStateBySwitchRequestData'

    def __init__(self, destination, correlation_id, switch_id):
        payload = {
            'clazz': self.JAVA_REQUEST_DATA,
            'action': 'dumpBySwitch',
            'switch_id': switch_id
        }

        super(DumpBySwitchCtrlCommandMessage, self).__init__(destination,
                                                 payload, correlation_id)


def create_list(correlation_id):
    return CtrlCommandMessage('*', correlation_id, 'list')


def create_dump_state(correlation_id, destination):
    return CtrlCommandMessage(destination, correlation_id, 'dump')


def create_dump_state_by_switch(correlation_id, destination, switch):
    return DumpBySwitchCtrlCommandMessage(destination, correlation_id, switch)


def create_resource_dump_state(correlation_id, destination):
    return CtrlCommandMessage(destination, correlation_id, 'dumpResorceCache')
