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

from topologylistener import config
from topologylistener import const
from topologylistener import context as context_module
from topologylistener import exc
from topologylistener import kafkareader
from topologylistener.messageclasses import MessageItem

raw_logger = logging.getLogger(__name__)


def main_loop():
    # pool_size = config.getint('gevent', 'worker.pool.size')
    # (crimi) - Setting pool_size to 1 to avoid deadlocks. This is until we are able to demonstrate that
    #           the deadlocks are able to be avoided.
    #           An improvement would be to do the DB updates on single worker, allowing everything else to
    #           happen concurrently. But expected load for 1.0 isn't great .. more than manageable with 1 worker.
    #
    pool_size = 1
    pool = gevent.pool.Pool(pool_size)
    raw_logger.info('Started gevent pool with size %d', pool_size)

    consumer = kafkareader.create_consumer(config)

    raw_logger.info('Ready to handle requests')

    while True:
        try:
            raw_message = kafkareader.read_message(consumer)
            context, message = unpack(raw_message)
            context.log(raw_logger).debug(
                    'Incoming message (see extra field %s)',
                    const.LOG_ATTR_JSON_PAYLOAD,
                    extra={const.LOG_ATTR_JSON_PAYLOAD: message})

            handler = gevent.Greenlet(event_handler, context, message)
            handler.link(RequestResultReporter(context))
            pool.start(handler)
        except exc.InvalidDecodeError as e:
            raw_logger.error(e)
            raw_logger.error(
                    'Raw input that lead to decode failure: %r', e.raw_request)
        except exc.Error as e:
            raw_logger.error('%s', e)
        except Exception as e:
            raw_logger.error('%s', e, exc_info=True)


def event_handler(context, message):
    log = context.log(raw_logger)

    log.debug('Enter event_handler')

    event = MessageItem(context, message)
    for attempt in range(5):
        log.debug('Request handling attempt #%d', attempt)

        try:
            event.handle()
            break

        except exc.RecoverableError:
            log.error('Attempt #%d end with failure', attempt)
        except exc.UnrecoverableError as e:
            log.error('Internal error during message processing',
                      exc_info=e.exc_info)
            break
        except exc.Error as e:
            log.error('%s', e)
            break

        # FIXME(surabujin): we should never do this
        except Exception:
            log.error(
                    'Unhandled exception during request processing',
                    exc_info=True)
            log.error(
                    'Treat ALL unhandled exceptions as recoverable errors '
                    '(MUST BE FIXED).')

        time.sleep(.2)

    else:
        log.error('All attempts to complete request have failed')

    log.debug('Leave event_handler')


def unpack(raw_message):
    try:
        message = json.loads(raw_message)
    except (TypeError, ValueError) as e:
        raise exc.InvalidDecodeError(e, raw_message)

    context = context_module.OperationContext(message)
    return context, message


class RequestResultReporter(object):
    def __init__(self, context):
        self.context = context

    def __call__(self, handler):
        log = self.context.log(raw_logger)
        if handler.successful():
            log.info('message processing is over')
        else:
            log.error(
                    'message processing failed (unhandled exception)',
                    exc_info=handler.exc_info)
