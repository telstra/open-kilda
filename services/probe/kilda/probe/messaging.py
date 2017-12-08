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

import kafka
import json
import pprint
import logging
import gevent
import sys
import time
from contextlib import contextmanager

LOG = logging.getLogger(__name__)


def send_with_context(context, message):
    send(context.kafka_bootstrap_servers, context.kafka_topic, message)


def send(bootstrap_servers, topic, message):
    producer = kafka.KafkaProducer(bootstrap_servers=bootstrap_servers)
    future = producer.send(topic, message)
    future.get(timeout=60)


@contextmanager
def receive_with_context_async(context):
    records = []

    def collector(record):
        try:
            data = json.loads(record.value)
            if (data['correlation_id'] == context.correlation_id and
                data['destination'] == 'CTRL_CLIENT'):
                LOG.debug('New message in topic:\n%s', pprint.pformat(data))
                records.append(record)
        except Exception:
            LOG.exception('error on %s', record)

    progress_green_thread = gevent.spawn(progress)

    green_thread = gevent.spawn(receive_with_context, context, collector)

    yield records

    green_thread.join(context.timeout)
    green_thread.kill()
    progress_green_thread.kill()
    sys.stdout.write("\r")
    sys.stdout.flush()


def receive_with_context(context, callback):
    receive(context.kafka_bootstrap_servers, context.kafka_topic, callback)


def receive(bootstrap_servers, topic, callback):
    consumer = kafka.KafkaConsumer(topic, bootstrap_servers=bootstrap_servers)
    for msg in consumer:
        callback(msg)


def progress():
    while True:
        sys.stderr.write('.')
        sys.stderr.flush()
        time.sleep(0.5)
