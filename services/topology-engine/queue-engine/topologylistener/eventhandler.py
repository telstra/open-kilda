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
import kafkareader

from topologylistener import config
from topologylistener import exc
from topologylistener.messageclasses import MessageItem

logger = logging.getLogger(__name__)


def main_loop():
    pool_size = config.getint('gevent', 'worker.pool.size')
    # pool_size = 1
    pool = gevent.pool.Pool(pool_size)
    logger.info('Started gevent pool with size %d', pool_size)

    consumer = kafkareader.create_consumer(config)

    logger.info('Ready to handle requests')

    while True:
        try:
            raw_event = kafkareader.read_message(consumer)
            logger.debug('READ MESSAGE %s', raw_event)
            message = unpack(raw_event)

            handler = gevent.Greenlet(event_handler, message)
            handler.link(RequestResultReporter())
            pool.start(handler)
        except exc.InvalidDecodeError as e:
            logger.error(e)
            logger.error(
                    'Raw input that lead to decode failure: %r', e.raw_request)
        except exc.Error as e:
            logger.error('%s', e)
        except Exception as e:
            logger.error('%s', e, exc_info=True)


def event_handler(message):
    logger.debug('Enter event_handler')

    event = MessageItem(message)
    for attempt in range(5):
        logger.debug('Request handling attempt #%d', attempt)

        try:
            event.handle()
            break

        except exc.RecoverableError:
            logger.error('Attempt #%d end with failure', attempt)
        except exc.UnrecoverableError as e:
            logger.error('Internal error during message processing',
                      exc_info=e.exc_info)
            break
        except exc.Error as e:
            logger.error('%s', e)
            break

        # FIXME(surabujin): we should never do this
        except Exception:
            logger.error(
                    'Unhandled exception during request processing',
                    exc_info=True)
            logger.error(
                    'Treat ALL unhandled exceptions as recoverable errors '
                    '(MUST BE FIXED).')

        time.sleep(.2)

    else:
        logger.error('All attempts to complete request have failed')

    logger.debug('Leave event_handler')


def unpack(raw_message):
    try:
        message = json.loads(raw_message)
    except (TypeError, ValueError) as e:
        raise exc.InvalidDecodeError(e, raw_message)
    return message


class RequestResultReporter(object):
    def __call__(self, handler):
        if handler.successful():
            logger.info('message processing is over')
        else:
            logger.error(
                    'message processing failed (unhandled exception)',
                    exc_info=handler.exc_info)
