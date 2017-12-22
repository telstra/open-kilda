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
from logger import get_logger
import config
import topology_reader

logger = get_logger()
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

    logger.info('Start gevent pool with size {}.'.format(pool_size))

    pool = gevent.pool.Pool(pool_size)

    consumer = kafkareader.create_consumer(config)

    topology_reader.read_topologies()

    while True:
        try:
            raw_event = kafkareader.read_message(consumer)
            event = MessageItem(**json.loads(raw_event))

            if "TOPOLOGY_ENGINE" != event.destination:
                logger.debug('Skip message for %s', event.destination)
                continue

            if event.get_message_type() in known_messages\
                    or event.get_command() in known_commands:
                logger.debug('Processing message for %s', event.destination)
                pool.spawn(topology_event_handler, event)

        except Exception as e:
            logger.exception(e.message)


def topology_event_handler(event):
    event_handled = False

    while not event_handled:
        event_handled = event.handle()

        if not event_handled:
            logger.error('Unable to process event: %s', event.get_type())
            logger.error('Message body: %s', event.to_json())
            time.sleep(.1)

    logger.debug('Event processed for: %s', event.get_type())
