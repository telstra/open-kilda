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
import time

import gevent
import gevent.pool
import gevent.queue

import config
import kafkareader
from messageclasses import MessageItem
from topologylistener import model

logger = logging.getLogger(__name__)

known_messages = ['org.openkilda.messaging.info.event.SwitchInfoData',
                  'org.openkilda.messaging.info.event.IslInfoData',
                  'org.openkilda.messaging.info.event.PortInfoData',
                  'org.openkilda.messaging.info.flow.FlowInfoData',
                  'org.openkilda.messaging.info.rule.SwitchFlowEntries',
                  'org.openkilda.messaging.error.rule.DumpRulesErrorData']
known_commands = ['org.openkilda.messaging.command.flow.FlowCreateRequest',
                  'org.openkilda.messaging.command.flow.FlowDeleteRequest',
                  'org.openkilda.messaging.command.flow.FlowUpdateRequest',
                  'org.openkilda.messaging.command.flow.FlowPathRequest',
                  'org.openkilda.messaging.command.flow.FlowGetRequest',
                  'org.openkilda.messaging.command.flow.FlowsGetRequest',
                  'org.openkilda.messaging.command.flow.FlowRerouteRequest',
                  'org.openkilda.messaging.command.system.FeatureToggleRequest',
                  'org.openkilda.messaging.command.system.FeatureToggleStateRequest',
                  'org.openkilda.messaging.command.switches.SwitchRulesSyncRequest',
                  'org.openkilda.messaging.command.switches.SwitchRulesValidateRequest',
                  'org.openkilda.messaging.command.discovery.NetworkCommandData',
                  'org.openkilda.messaging.command.FlowsSyncRequest',
                  'org.openkilda.messaging.te.request.LinkPropsDrop',
                  'org.openkilda.messaging.te.request.LinkPropsPut']


def main_loop():
    # pool_size = config.getint('gevent', 'worker.pool.size')
    # (crimi) - Setting pool_size to 1 to avoid deadlocks. This is until we are able to demonstrate that
    #           the deadlocks are able to be avoided.
    #           An improvement would be to do the DB updates on single worker, allowing everything else to
    #           happen concurrently. But expected load for 1.0 isn't great .. more than manageable with 1 worker.
    #
    pool_size = 1
    pool = gevent.pool.Pool(pool_size)
    logger.info('Started gevent pool with size %d', pool_size)

    consumer = kafkareader.create_consumer(config)

    while True:
        try:
            raw_event = kafkareader.read_message(consumer)
            logger.debug('READ MESSAGE %s', raw_event)
            event = MessageItem(json.loads(raw_event))

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
            logger.error('Message body: %s', dump_object(event))
            time.sleep(.1)

    logger.debug('Event processed for: %s, correlation_id: %s',
                 event.get_type(), event.correlation_id)


def dump_object(o):
    return json.dumps(o, cls=model.JSONEncoder, sort_keys=True, indent=4)
