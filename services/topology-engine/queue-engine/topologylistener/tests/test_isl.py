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

import logging
import unittest
import uuid

import py2neo

from topologylistener import flow_utils
from topologylistener import messageclasses
from topologylistener import message_utils
from topologylistener import model

dpid_test_marker = 0xfffe000000000000
dpid_protected_bits = 0xffffff0000000000

neo4j_connect = flow_utils.graph


def clean_neo4j_test_data(tx):
    drop_test_isls(tx)
    drop_test_switches(tx)


def drop_test_isls(tx):
    q = 'MATCH (:switch)-[self:isl]->() RETURN self'
    for data_set in tx.run(q):
        isl = data_set['self']
        if not is_test_dpid(isl['src_switch']):
            continue
        if not is_test_dpid(isl['dst_switch']):
            continue

        tx.separate(isl)


def drop_test_switches(tx):
    selector = py2neo.NodeSelector(tx)
    for sw in selector.select('switch'):
        if not is_test_dpid(sw['name']):
            continue
        tx.delete(sw)


def make_isl_pair(tx, source, dest):
    template = {
        'clazz': messageclasses.MT_ISL,
        'state': 'DISCOVERED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000,
        'path': [
            {
                'switch_id': make_datapath_id(1),
                'port_no': 2},
            {
                'switch_id': make_datapath_id(2),
                'port_no': 4}]}

    for nodes in (
            (source, dest),
            (dest, source)):
        payload = template.copy()
        payload['path'] = [
            {'switch_id': x.dpid, 'port_no': x.port} for x in nodes]

        command = make_command(payload)

        messageclasses.MessageItem(**command).handle()


def make_command(payload):
    return {
        'payload': payload,
        'clazz': message_utils.MT_INFO,
        'timestamp': 0,
        'correlation_id': make_correlation_id('test-isl')}


def make_correlation_id(prefix=''):
    if prefix and prefix[-1] != '.':
        prefix += '.'
    return '{}{}'.format(prefix, uuid.uuid1())


def make_datapath_id(number):
    if number & dpid_protected_bits:
        raise ValueError(
                'Invalid switch id {}: use protected bits'.format(number))
    return pack_dpid(number | dpid_test_marker)


def is_test_dpid(dpid):
    dpid = unpack_dpid(dpid)
    return dpid & dpid_protected_bits == dpid_test_marker


def pack_dpid(dpid):
    value = hex(dpid)
    i = iter(value)
    chunked = [a + b for a, b in zip(i, i)]
    chunked.pop(0)
    return ':'.join(chunked)


def unpack_dpid(dpid_str):
    value = dpid_str.replace(':', '')
    return int(value, 16)


class TestIsl(unittest.TestCase):
    switch_nodes = [
        model.NetworkEndpoint(make_datapath_id(1), 2),
        model.NetworkEndpoint(make_datapath_id(2), 4)]

    def setUp(self):
        logging.basicConfig(level=logging.INFO)
        with neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)

            make_isl_pair(tx, *self.switch_nodes[:2])

    def tearDown(self):
        with neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)

    def test_isl_set_cost(self):
        for isl in self.enumerate_isl(*self.switch_nodes[:2]):
            self.assertFalse('cost' in isl)

        sw = self.switch_nodes[0]
        payload = {
            'clazz': messageclasses.MT_PORT,
            'state': 'DOWN',
            'switch_id': sw.dpid,
            'port_no': sw.port}
        command = make_command(payload)

        result = messageclasses.MessageItem(**command).handle()
        self.assertTrue(result, 'Port DOWN command have failed')

        for idx, isl in enumerate(self.enumerate_isl(*self.switch_nodes[:2])):
            if not idx:
                self.assertTrue(
                        'cost' in isl,
                        'There is no "cost" property in ISL {!r}'.format(isl))
                self.assertEqual(
                        isl['cost'], 10000,
                        'Bad "cost" value {!r} in isl {!r}'.format(
                                isl['cost'], isl))
            else:
                self.assertTrue(
                        'cost' not in isl,
                        'Extra/peer record receive "cost" update during port '
                        'DOWN')

    def enumerate_isl(self, *endpoints):
        with neo4j_connect.begin() as tx:
            for node in endpoints:
                q = (
                    'MATCH (a:switch)-[self:isl]->(:switch)\n'
                    'WHERE a.name={dpid}\n'
                    'RETURN self')

                for data_set in tx.run(q, dpid=node.dpid):
                    node = data_set['self']
                    neo4j_connect.pull(node)
                    yield node
