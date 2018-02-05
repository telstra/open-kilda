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

import kafkareader
import json
import time

import gevent
import gevent.pool
import gevent.queue

from messageclasses import MessageItem
import logging
import config

logger = logging.getLogger(__name__)

known_messages = ['org.openkilda.messaging.info.event.SwitchInfoData',
                  'org.openkilda.messaging.info.event.IslInfoData',
                  'org.openkilda.messaging.info.event.PortInfoData',
                  'org.openkilda.messaging.info.flow.FlowInfoData']
known_commands = ['org.openkilda.messaging.command.flow.FlowCreateRequest',
                  'org.openkilda.messaging.command.flow.FlowDeleteRequest',
                  'org.openkilda.messaging.command.flow.FlowUpdateRequest',
                  'org.openkilda.messaging.command.flow.FlowPathRequest',
                  'org.openkilda.messaging.command.flow.FlowGetRequest',
                  'org.openkilda.messaging.command.flow.FlowsGetRequest',
                  'org.openkilda.messaging.command.flow.FlowRerouteRequest',
                  'org.openkilda.messaging.command.discovery.NetworkCommandData']


def main_loop():
    pool_size = config.getint('gevent', 'worker.pool.size')
    pool = gevent.pool.Pool(pool_size)
    logger.info('Started gevent pool with size %d', pool_size)

    consumer = kafkareader.create_consumer(config)

    while True:
        try:
            raw_event = kafkareader.read_message(consumer)
            logger.debug('READ MESSAGE %s', raw_event)
            event = MessageItem(**json.loads(raw_event))

            if event.get_message_type() in known_messages\
                    or event.get_command() in known_commands:
                pool.spawn(topology_event_handler, event)
            else:
              logger.debug('Received unknown type or command %s', raw_event)

        except Exception as e:
            logger.exception(e.message)


def topology_event_handler(event):
    event_handled = False

    attempts = 0
    while not event_handled and attempts < 5:
        event_handled = event.handle()
        attempts += 1
        if not event_handled:
            logger.error('Unable to process event: %s', event.get_type())
            logger.error('Message body: %s', event.to_json())
            time.sleep(.1)

    logger.debug('Event processed for: %s', event.get_type())
