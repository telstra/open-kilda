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

import itertools
import logging
import sys
import unittest
import uuid

from topologylistener import flow_utils
from topologylistener import message_utils
from topologylistener import messageclasses

log = logging.getLogger(__name__)


def exec_isl_discovery(isl, **fields):
    payload = payload_isl_info(isl, **fields)
    return messageclasses.MessageItem(**command(payload)).handle()


def payload_isl_info(isl, **fields):
    payload = {
        'state': 'DISCOVERED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000}
    payload.update(fields)
    payload.update({
        'clazz': messageclasses.MT_ISL,
        'path': [
            {
                'switch_id': isl.source.dpid,
                'port_no': isl.source.port},
            {
                'switch_id': isl.dest.dpid,
                'port_no': isl.dest.port}]})

    return payload


def command(payload, **fields):
    message = {
        'timestamp': 0,
        'correlation_id': make_correlation_id('test')}
    message.update(fields)
    message.update({
        'clazz': message_utils.MT_INFO,
        'payload': payload})
    return message


def make_correlation_id(prefix=''):
    if prefix and prefix[-1] != '.':
        prefix += '.'
    return '{}{}'.format(prefix, uuid.uuid1())


class Environment(object):
    def __init__(self):
        self._monkey_patch_recovery = {}
        self.kafka_producer_stub = KafkaProducerStub()

        self.init_logging()
        self.neo4j_connect = self.init_neo4j()

        self.monkey_patch()

    def init_logging(self):
        logging.basicConfig(level=logging.DEBUG, stream=sys.stdout)

    def init_neo4j(self):
        return flow_utils.graph

    def monkey_patch(self):
        for module, attr, replace in (
                (message_utils, 'producer', self.kafka_producer_stub),):
            current = getattr(module, attr)
            if current is replace:
                continue

            module_data = self._monkey_patch_recovery.setdefault(module, {})
            module_data[attr] = current
            setattr(module, attr, replace)

        message_utils.producer = self.kafka_producer_stub


class AbstractTest(unittest.TestCase):
    def setUp(self):
        separator = '*-' * 29 + '*'
        prefix = '*' * 3
        message = '\n'.join((
            '', separator,
            '{} Run test {}'.format(prefix, self.id()),
            separator))
        logging.info(message)


class KafkaProducerStub(object):
    def __init__(self, backlog_size=32):
        self.backlog_size = backlog_size
        self.backlog = []

    def send(self, topic, payload=None):
        record = KafkaSendRecord(topic, payload)
        self.backlog.insert(0, record)
        self.backlog[self.backlog_size:] = []
        return KafkaSendFutureStub(record)


class KafkaSendFutureStub(object):
    def __init__(self, record):
        self.record = record

    def get(self, timeout=None):
        log.debug('Send kafka record: %s', self.record)


class KafkaSendRecord(object):
    payload_visibility_limit = 60
    _counter = itertools.count()

    def __init__(self, topic, payload):
        self.topic = topic
        self.payload = payload
        self.index = next(self._counter)

    def __str__(self):
        payload = self.payload
        if not isinstance(payload, basestring):
            payload = str(payload)

        chunks = [
            'index={}'.format(self.index),
            'topic={!r}'.format(self.topic)]
        if len(payload) < self.payload_visibility_limit:
            chunks.append('payload={!r}'.format(payload))
        else:
            chunks.append('payload="""{!r}""" ... more {} chars'.format(
                payload[:self.payload_visibility_limit],
                len(payload) - self.payload_visibility_limit))
        return 'KafkaSend{{{}}}'.format(', '.join(chunks))


# must be at the end of module
env = Environment()
