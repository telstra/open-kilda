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

import json
import logging
import threading

from topologylistener import model

import flow_utils
import message_utils

logger = logging.getLogger(__name__)

MT_SYNC_REQUEST = "org.openkilda.messaging.command.switches.SwitchRulesSyncRequest"

# This is used for blocking on flow changes.
# flow_sem = multiprocessing.Semaphore()
neo4j_update_lock = threading.RLock()


class MessageItem(model.JsonSerializable):
    def __init__(self, message):
        self._raw_message = message

        self.type = message.get("clazz")
        self.payload = message.get("payload", {})
        self.destination = message.get("destination", "")
        self.correlation_id = message.get("correlation_id", "admin-request")
        self.reply_to = message.get("reply_to", "")

        try:
            timestamp = message['timestamp']
            timestamp = model.TimeProperty.new_from_java_timestamp(timestamp)
        except KeyError:
            timestamp = model.TimeProperty.now()
        self.timestamp = timestamp

    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_type(self):
        message_type = self.get_message_type()
        command = self.get_command()
        return command if message_type == 'unknown' else message_type

    def get_command(self):
        return self.payload.get('clazz', 'unknown')

    def get_message_type(self):
        return self.payload.get('clazz', 'unknown')

    def handle(self):
        try:
            event_handled = False

            if self.get_message_type() == MT_SYNC_REQUEST:
                event_handled = self.sync_switch_rules()

            return event_handled

        except Exception as e:
            logger.exception("Exception during handling message")
            return False

    def sync_switch_rules(self):
        switch_id = self.payload['switch_id']
        rules_to_sync = self.payload['rules']

        logger.debug('Switch rules synchronization for rules: %s', rules_to_sync)

        commands, installed_rules = flow_utils.build_commands_to_sync_rules(
            switch_id, rules_to_sync)
        if commands:
            indent = ' ' * 4
            logger.info('Install commands for switch %s are to be sent:\n%s%s',
                        switch_id, indent,
                        (',\n' + indent).join(str(x) for x in commands))
            message_utils.send_force_install_commands(switch_id, commands,
                                                      self.correlation_id)

        message_utils.send_sync_rules_response(
            installed_rules, self.correlation_id)

        return True
