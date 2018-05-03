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
from topologylistener import isl_utils
from topologylistener import messageclasses
from topologylistener import message_utils
from topologylistener import model

ISL_STATUS_ACTIVE = 'active'
ISL_STATUS_INACTIVE = 'inactive'

dpid_test_marker = 0xfffe000000000000
dpid_protected_bits = 0xffffff0000000000

neo4j_connect = flow_utils.graph


def clean_neo4j_test_data(tx):
    drop_db_isls(tx)
    drop_db_switches(tx)
    drop_db_link_props(tx)


def inject_db_link_props(tx, source, dest, payload):
    match = {
        'src_switch': source.dpid,
        'src_port': source.port,
        'dst_switch': dest.dpid,
        'dst_port': dest.port,
    }
    p = match.copy()
    p['link_props'] = match.copy()
    p['link_props'].update(payload)

    q = (
        'MERGE (subject:link_props {\n'
        '  src_switch: $src_switch,'
        '  src_port: $src_port,'
        '  dst_switch: $dst_switch,'
        '  dst_port: $dst_port})\n'
        'SET subject = $link_props')
    tx.run(q, p)


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
    selector = py2neo.NodeSelector(tx)
    for node in selector.select('switch'):
        if not is_test_dpid(node['name']):
            continue
        tx.delete(node)


def drop_db_link_props(tx):
    selector = py2neo.NodeSelector(tx)
    for node in selector.select('link_props'):
        match = [is_test_dpid(node[rel]) for rel in 'src_switch', 'dst_switch']
        if not all(match):
            continue
        tx.delete(node)


def make_switch_add(endpoint):
    payload = {
        'clazz': messageclasses.MT_SWITCH,
        'state': 'ADDED',
        'switch_id': endpoint.dpid,
        'address': '172.16.0.64',
        'hostname': 'test-sw-{}'.format(endpoint.dpid.replace(':', '')),
        'description': 'test switch',
        'controller': '172.16.0.1'}
    command = make_command(payload)
    return messageclasses.MessageItem(**command).handle()


def make_switch_remove(endpoint):
    payload = {
        'clazz': messageclasses.MT_SWITCH,
        'state': 'REMOVED',
        'switch_id': endpoint.dpid,
        'address': '172.16.0.64',
        'hostname': 'test-sw-{}'.format(endpoint.dpid.replace(':', '')),
        'description': 'test switch',
        'controller': '172.16.0.1'}
    command = make_command(payload)
    return messageclasses.MessageItem(**command).handle()


def make_port_down(endpoint):
    payload = {
        'clazz': messageclasses.MT_PORT,
        'state': 'DOWN',
        'switch_id': endpoint.dpid,
        'port_no': endpoint.port}
    command = make_command(payload)

    return messageclasses.MessageItem(**command).handle()


def make_isl_pair(source, dest):
    make_isl_discovery(source, dest)
    make_isl_discovery(dest, source)


def make_isl_discovery(source, dest):
    command = make_command({
        'clazz': messageclasses.MT_ISL,
        'state': 'DISCOVERED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000,
        'path': [
            {
                'switch_id': source.dpid,
                'port_no': source.port},
            {
                'switch_id': dest.dpid,
                'port_no': dest.port}]})
    return messageclasses.MessageItem(**command).handle()


def make_isl_failed(source):
    command = make_command({
        'clazz': messageclasses.MT_ISL,
        'state': 'FAILED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000,
        'path': [
            {
                'switch_id': source.dpid,
                'port_no': source.port}]})
    return messageclasses.MessageItem(**command).handle()


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
    src_endpoint = model.NetworkEndpoint(make_datapath_id(1), 2)
    dst_endpoint = model.NetworkEndpoint(make_datapath_id(2), 4)

    def setUp(self):
        logging.basicConfig(level=logging.INFO)
        with neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)

    def tearDown(self):
        with neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)

    def test_isl_status(self):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint

        make_switch_add(src_endpoint)
        make_switch_add(dst_endpoint)

        status_down = {'actual': ISL_STATUS_INACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_half_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_ACTIVE}

        # 0 0
        self.assertTrue(make_isl_discovery(src_endpoint, dst_endpoint))

        # 1 0
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_half_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_down)
        self.assertTrue(make_isl_discovery(dst_endpoint, src_endpoint))

        # 1 1
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_up)
        self.assertTrue(make_isl_failed(src_endpoint))

        # 0 1
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_down)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_half_up)
        self.assertTrue(make_isl_discovery(src_endpoint, dst_endpoint))

        # 1 1
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_up)
        self.assertTrue(make_isl_failed(dst_endpoint))

        # 1 0
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_half_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_down)
        self.assertTrue(make_isl_discovery(dst_endpoint, src_endpoint))

        # 1 1
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_up)
        self.assertTrue(make_isl_failed(src_endpoint))

        # 0 1
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_down)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_half_up)
        self.assertTrue(make_isl_failed(dst_endpoint))

        # 0 0
        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_down)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_down)

    def test_switch_unplug(self):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint

        self.assertTrue(make_switch_add(src_endpoint))
        self.assertTrue(make_switch_add(dst_endpoint))
        self.assertTrue(make_isl_discovery(src_endpoint, dst_endpoint))
        self.assertTrue(make_isl_discovery(dst_endpoint, src_endpoint))

        status_down = {'actual': ISL_STATUS_INACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_half_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_ACTIVE}

        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_up)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_up)

        self.assertTrue(make_switch_remove(src_endpoint))

        self.ensure_isl_props(neo4j_connect, src_endpoint, dst_endpoint, status_down)
        self.ensure_isl_props(neo4j_connect, dst_endpoint, src_endpoint, status_half_up)

    def test_cost_raise_on_port_down(self):
        self.setup_initial_data()
        self._cost_raise_on_port_down(1, 10000, 10000)

    def test_cost_raise_on_port_down_without_link_props(self):
        self.setup_initial_data(make_link_props=False)
        self._cost_raise_on_port_down(None, 10000, 10000)

    def _cost_raise_on_port_down(self, initial, down, recover):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint

        self.ensure_isl_costs(
            (src_endpoint, initial),
            (dst_endpoint, initial))

        self.assertTrue(
            make_port_down(src_endpoint), 'Port DOWN command have failed')

        self.ensure_isl_costs(
            (src_endpoint, down),
            (dst_endpoint, down))

        self.assertTrue(
            make_isl_discovery(src_endpoint, dst_endpoint),
            'ISL discovery command have failed')

        self.ensure_isl_costs(
            (src_endpoint, recover),
            (dst_endpoint, recover))

    def test_no_cost_raise_on_isl_down(self):
        self.setup_initial_data()

        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint

        self.ensure_isl_costs(
                (src_endpoint, 1),
                (dst_endpoint, 1))

        self.assertTrue(
                make_isl_failed(src_endpoint), 'Port DOWN command have failed')

        self.ensure_isl_costs(
                (src_endpoint, 1),
                (dst_endpoint, 1))

        self.assertTrue(
                make_isl_discovery(src_endpoint, dst_endpoint),
                'ISL discovery command have failed')

        self.ensure_isl_costs(
                (src_endpoint, 1),
                (dst_endpoint, 1))

    def ensure_isl_props(self, tx, source, dest, props):
        isl_record = isl_utils.fetch(tx, model.InterSwitchLink(source, dest, None))
        neo4j_connect.pull(isl_record)
        for name in props:
            self.assertEqual(props[name], isl_record[name], "Invalid ISL's {!r} value ({!r})".format(name, isl_record))

    def ensure_isl_costs(self, *endpoint_cost_pairs):
        endpoints = []
        costs = []
        for pair in endpoint_cost_pairs:
            endpoints.append(pair[0])
            costs.append(pair[1])

        for isl, expect in zip(self.enumerate_isl(*endpoints), costs):
            self.assertEqual(
                    expect, isl['cost'],
                    'Bad "cost" value {!r} (expect {!r}) in isl {!r}'.format(
                            isl['cost'], expect, isl))

    def enumerate_isl(self, *endpoints):
        with neo4j_connect.begin() as tx:
            for node in endpoints:
                q = (
                    'MATCH (a:switch)-[self:isl]->(:switch)\n'
                    'WHERE a.name=$dpid\n'
                    'RETURN self')

                for data_set in tx.run(q, dpid=node.dpid):
                    node = data_set['self']
                    neo4j_connect.pull(node)
                    yield node

    def setup_initial_data(self, make_link_props=True):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint

        make_switch_add(src_endpoint)
        make_switch_add(dst_endpoint)

        if make_link_props:
            with neo4j_connect.begin() as tx:
                payload = {'cost': 1}
                inject_db_link_props(tx, src_endpoint, dst_endpoint, payload)
                inject_db_link_props(tx, dst_endpoint, src_endpoint, payload)

        make_isl_pair(src_endpoint, dst_endpoint)
