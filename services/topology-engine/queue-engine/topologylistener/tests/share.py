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

import functools
import itertools
import json
import logging
import os
import sys
import unittest
import uuid

from topologylistener import db
from topologylistener import flow_utils
from topologylistener import message_utils
from topologylistener import messageclasses

log = logging.getLogger(__name__)

dpid_test_marker = 0xfffe000000000000
dpid_protected_bits = 0xffffff0000000000

dpid_test_marker = 0xfffe000000000000
dpid_protected_bits = 0xffffff0000000000

cookie_test_data_flag = 0x0010000000000000


def feed_isl_discovery(isl, **fields):
    payload = isl_info_payload(isl, **fields)
    return feed_message(command(payload))


def feed_message(message):
    return messageclasses.MessageItem(message).handle()


def link_props_request(link_props):
    return {
        'source': {
            'switch-id': link_props.source.dpid,
            'port-id': link_props.source.port},
        'dest': {
            'switch-id': link_props.dest.dpid,
            'port-id': link_props.dest.port},
        'props': link_props.props,
        'time_create': link_props.time_create.as_java_timestamp(),
        'time_modify': link_props.time_modify.as_java_timestamp()}


def link_props_put_payload(request):
    return {
        'link_props': request,
        'clazz': messageclasses.CD_LINK_PROPS_PUT}


def link_props_drop_payload(request):
    return {
        'lookup_mask': request,
        'clazz': messageclasses.CD_LINK_PROPS_DROP}


def feature_toggle_request(**fields):
    payload = dict(fields)
    payload['clazz'] = (
        'org.openkilda.messaging.command.system.FeatureToggleRequest')
    return payload


def isl_info_payload(isl, **fields):
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


def switch_rules_sync(switch, rules=None):
    if not rules:
        rules = []
    return {
        'clazz': messageclasses.MT_SYNC_REQUEST,
        'switch_id': switch,
        'rules': rules}


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


def make_datapath_id(number):
    if number & dpid_protected_bits:
        raise ValueError(
                'Invalid switch id {}: use protected bits'.format(number))
    return long_as_dpid(number | dpid_test_marker)


def clean_neo4j_test_data(tx):
    drop_db_flows(tx)
    drop_db_isls(tx)
    drop_db_switches(tx)
    drop_db_link_props(tx)


def drop_db_isls(tx):
    q = 'MATCH (:switch)-[self:isl|:link_props]->() RETURN self'
    for data_set in tx.run(q):
        rel = data_set['self']
        if not is_test_dpid(rel['src_switch']):
            continue
        if not is_test_dpid(rel['dst_switch']):
            continue

        tx.separate(rel)


def drop_db_switches(tx):
    q = 'MATCH (a:switch) RETURN a'
    batch = (x['a'] for x in tx.run(q))
    for node in batch:
        if not is_test_dpid(node['name']):
            continue
        tx.delete(node)


def drop_db_link_props(tx):
    q = 'MATCH (a:link_props) RETURN a'
    batch = (x['a'] for x in tx.run(q))
    for node in batch:
        match = [
            is_test_dpid(node[rel]) for rel in ('src_switch', 'dst_switch')]
        if not all(match):
            continue
        tx.delete(node)


def drop_db_flows(tx):
    q_lookup = 'MATCH (:switch)-[a:flow]->(:switch) RETURN a'
    q_delete = 'MATCH (:switch)-[a:flow]->(:switch) WHERE id(a)=$id DELETE a'
    batch = (x['a'] for x in tx.run(q_lookup))
    for relation in batch:
        cookie = relation['cookie']
        if cookie is None:
            continue
        try:
            cookie = int(cookie)
            if not (cookie & cookie_test_data_flag):
                continue
        except ValueError:
            continue

        drop_db_flow_segments(tx, relation['flowid'])
        tx.run(q_delete, {'id': db.neo_id(relation)})


def drop_db_flow_segments(tx, flow_id):
    q = (
        'MATCH (:switch)-[fs:flow_segment]->(:switch)\n'
        'WHERE fs.flowid=$flow_id\n'
        'DELETE fs')
    tx.run(q, {'flow_id': flow_id})


def dpid_to_test_dpid(raw):
    dpid = dpid_as_long(raw)
    dpid &= ~dpid_protected_bits
    return long_as_dpid(dpid | dpid_test_marker)


def is_test_dpid(dpid):
    dpid = dpid_as_long(dpid)
    return dpid & dpid_protected_bits == dpid_test_marker


def dpid_as_long(dpid_str):
    value = dpid_str.replace(':', '')
    return int(value, 16)


def long_as_dpid(dpid):
    value = hex(dpid)
    i = iter(value)
    chunked = [a + b for a, b in zip(i, i)]
    chunked.pop(0)
    return ':'.join(chunked)


class Environment(object):
    def __init__(self):
        self._monkey_patch_recovery = {}
        self.kafka_producer_stub = KafkaProducerStub()

        self.init_logging()
        self.neo4j_connect = self.init_neo4j()

        self.monkey_patch()

    def kafka_producer_backlog(self):
        return tuple(self.kafka_producer_stub.backlog)

    def reset_kafka_producer(self):
        self.kafka_producer_stub.backlog[:] = []

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
    path = functools.partial(os.path.join, os.path.dirname(__file__))
    correlation_id_counter = itertools.count()

    def setUp(self):
        self.log_separator()
        self.drop_persistent_data()

    def log_separator(self):
        separator = '*-' * 29 + '*'
        prefix = '*' * 3
        message = '\n'.join((
            '', separator,
            '{} Run test {}'.format(prefix, self.id()),
            separator))
        sys.stdout.flush()
        logging.info(message)

    def drop_persistent_data(self):
        with env.neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)

    def feed_service(self, message, can_fail=False):
        result = messageclasses.MessageItem(message).handle()
        if not can_fail:
            self.assertTrue(result)

    def take_kafka_response(self, topic,
                            offset=0, expect_class=message_utils.MT_INFO):
        kafka_backlog = env.kafka_producer_stub.backlog

        self.assertTrue(offset < len(kafka_backlog))

        kafka_record = kafka_backlog[offset]
        self.assertEqual(topic, kafka_record.topic)

        message = json.loads(kafka_record.payload)
        self.assertEqual(expect_class, message['clazz'])

        return message['payload']

    def open_neo4j_session(self):
        return env.neo4j_connect.begin()

    def allow_all_features(self):
        features = {
            x: True
            for x in messageclasses.features_status_transport_to_app_map}
        features_request = feature_toggle_request(**features)
        self.assertTrue(feed_message(command(features_request)))

    def make_correlation_id(self):
        return '{}-{}'.format(self.id(), next(self.correlation_id_counter))

    def load_flow_request(self, name):
        request = self.load_data(name)
        flow_info_data = request['payload']
        self.fix_direction_markers(flow_info_data)
        self.put_test_flow_marker(flow_info_data)
        return request

    def load_data(self, name):
        with open(self.path('data', name), 'rt') as stream:
            return json.load(stream)

    @staticmethod
    def put_test_flow_marker(flow_info_data):
        flow_threads = flow_info_data['payload']
        for thread in flow_threads.values():
            thread['cookie'] |= cookie_test_data_flag

            for switch_key in ('src_switch', 'dst_switch'):
                thread[switch_key] = dpid_to_test_dpid(thread[switch_key])
            for path_segment in thread['flowpath']['path']:
                path_segment['switch_id'] = dpid_to_test_dpid(
                    path_segment['switch_id'])

    @classmethod
    def fix_direction_markers(cls, flow_info_data):
        flow_threads = flow_info_data['payload']
        direction_to_flag = {
            'forward': flow_utils.cookie_flag_forward,
            'reverse': flow_utils.cookie_flag_reverse}
        for direction in flow_threads:
            cookie = cls.clear_flow_cookie_flags(
                flow_threads[direction]['cookie'])
            cookie |= direction_to_flag[direction]
            flow_threads[direction]['cookie'] = cookie

    @staticmethod
    def clear_flow_cookie_flags(cookie):
        return cookie & ~flow_utils.cookie_flags_mask


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
