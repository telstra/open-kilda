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

import unittest

from topologylistener import exc
from topologylistener import isl_utils
from topologylistener import messageclasses
from topologylistener import model
from topologylistener.tests import share

ISL_STATUS_ACTIVE = 'active'
ISL_STATUS_INACTIVE = 'inactive'
ISL_STATUS_MOVED = 'moved'

ISL_STATUS_PROPS_DOWN = {
    'actual': ISL_STATUS_INACTIVE, 'status': ISL_STATUS_INACTIVE}
ISL_STATUS_PROPS_HALF_UP = {
    'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_INACTIVE}
ISL_STATUS_PROPS_UP = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_ACTIVE}
ISL_STATUS_PROPS_MOVED = {
    'actual': ISL_STATUS_MOVED, 'status': ISL_STATUS_MOVED}

neo4j_connect = share.env.neo4j_connect


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


def make_switch_add(dpid):
    payload = {
        'clazz': messageclasses.MT_SWITCH,
        'state': 'ADDED',
        'switch_id': dpid,
        'address': '172.16.0.64',
        'hostname': 'test-sw-{}'.format(dpid.replace(':', '')),
        'description': 'test switch',
        'controller': '172.16.0.1'}
    command = share.command(payload)
    return messageclasses.MessageItem(command).handle()


def make_switch_remove(dpid):
    payload = {
        'clazz': messageclasses.MT_SWITCH,
        'state': 'REMOVED',
        'switch_id': dpid,
        'address': '172.16.0.64',
        'hostname': 'test-sw-{}'.format(dpid.replace(':', '')),
        'description': 'test switch',
        'controller': '172.16.0.1'}
    command = share.command(payload)
    return messageclasses.MessageItem(command).handle()


def make_port_down(endpoint):
    payload = {
        'clazz': messageclasses.MT_PORT,
        'state': 'DOWN',
        'switch_id': endpoint.dpid,
        'port_no': endpoint.port}
    command = share.command(payload)

    return messageclasses.MessageItem(command).handle()


def make_isl_pair(source, dest):
    forward = model.InterSwitchLink(source, dest, None)
    share.feed_isl_discovery(forward)
    share.feed_isl_discovery(forward.reversed())


def make_isl_failed(source):
    command = share.command({
        'clazz': messageclasses.MT_ISL,
        'state': 'FAILED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000,
        'path': [
            {
                'switch_id': source.dpid,
                'port_no': source.port}]})
    return messageclasses.MessageItem(command).handle()


def make_isl_moved(isl):
    command = share.command({
        'clazz': messageclasses.MT_ISL,
        'state': 'MOVED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000,
        'path': [
            {
                'switch_id': isl.source.dpid,
                'port_no': isl.source.port
            },
            {
                'switch_id': isl.dest.dpid,
                'port_no': isl.dest.port
            }
        ]
    })
    return messageclasses.MessageItem(command).handle()


@unittest.skip('Tests do not work because of eliminating of cache topic')
class TestIsl(share.AbstractTest):
    src_endpoint = model.IslPathNode(share.make_datapath_id(1), 2)
    dst_endpoint = model.IslPathNode(share.make_datapath_id(2), 4)

    def test_isl_status(self):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint
        forward = model.InterSwitchLink(src_endpoint, dst_endpoint, None)
        reverse = forward.reversed()

        make_switch_add(src_endpoint.dpid)
        make_switch_add(dst_endpoint.dpid)

        status_down = {'actual': ISL_STATUS_INACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_half_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_INACTIVE}
        status_up = {'actual': ISL_STATUS_ACTIVE, 'status': ISL_STATUS_ACTIVE}

        # 0 0
        self.assertTrue(share.feed_isl_discovery(forward))

        # 1 0
        self.ensure_isl_props(neo4j_connect, forward, status_half_up)
        self.ensure_isl_props(neo4j_connect, reverse, status_down)
        self.assertTrue(share.feed_isl_discovery(reverse))

        # 1 1
        self.ensure_isl_props(neo4j_connect, forward, status_up)
        self.ensure_isl_props(neo4j_connect, reverse, status_up)
        self.assertTrue(make_isl_failed(src_endpoint))

        # 0 1
        self.ensure_isl_props(neo4j_connect, forward, status_down)
        self.ensure_isl_props(neo4j_connect, reverse, status_half_up)
        self.assertTrue(share.feed_isl_discovery(forward))

        # 1 1
        self.ensure_isl_props(neo4j_connect, forward, status_up)
        self.ensure_isl_props(neo4j_connect, reverse, status_up)
        self.assertTrue(make_isl_failed(dst_endpoint))

        # 1 0
        self.ensure_isl_props(neo4j_connect, forward, status_half_up)
        self.ensure_isl_props(neo4j_connect, reverse, status_down)
        self.assertTrue(share.feed_isl_discovery(reverse))

        # 1 1
        self.ensure_isl_props(neo4j_connect, forward, status_up)
        self.ensure_isl_props(neo4j_connect, reverse, status_up)
        self.assertTrue(make_isl_failed(src_endpoint))

        # 0 1
        self.ensure_isl_props(neo4j_connect, forward, status_down)
        self.ensure_isl_props(neo4j_connect, reverse, status_half_up)
        self.assertTrue(make_isl_failed(dst_endpoint))

        # 0 0
        self.ensure_isl_props(neo4j_connect, forward, status_down)
        self.ensure_isl_props(neo4j_connect, reverse, status_down)

    @unittest.skip('ISL conflict resolution was disabled, because event topology is responsible for them now')
    def test_isl_replug(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        sw_gamma = share.make_datapath_id(3)
        sw_delta = share.make_datapath_id(4)

        isl_alpha_beta = model.InterSwitchLink(
                model.IslPathNode(sw_alpha, 2),
                model.IslPathNode(sw_beta, 2), None)
        isl_beta_alpha = isl_alpha_beta.reversed()
        isl_beta_gamma = model.InterSwitchLink(
                model.IslPathNode(sw_beta, 2),
                model.IslPathNode(sw_gamma, 3), None)
        isl_gamma_beta = isl_beta_gamma.reversed()
        isl_gamma_delta = model.InterSwitchLink(
                model.IslPathNode(sw_gamma, 3),
                model.IslPathNode(sw_delta, 3), None)
        isl_delta_gamma = isl_gamma_delta.reversed()

        for dpid in sw_alpha, sw_beta, sw_gamma, sw_delta:
            self.assertTrue(make_switch_add(dpid))

        self.assertTrue(share.feed_isl_discovery(isl_alpha_beta))
        self.assertTrue(share.feed_isl_discovery(isl_beta_alpha))
        self.assertTrue(share.feed_isl_discovery(isl_gamma_delta))
        self.assertTrue(share.feed_isl_discovery(isl_delta_gamma))

        self.ensure_isl_props(
                neo4j_connect, isl_alpha_beta, ISL_STATUS_PROPS_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_beta_alpha, ISL_STATUS_PROPS_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_gamma_delta, ISL_STATUS_PROPS_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_delta_gamma, ISL_STATUS_PROPS_UP)

        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_beta_gamma)
        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_gamma_beta)

        # replug
        self.assertTrue(
                share.feed_isl_discovery(isl_beta_gamma))
        # alpha <-> beta is down
        self.ensure_isl_props(
                neo4j_connect, isl_alpha_beta, ISL_STATUS_PROPS_HALF_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_beta_alpha, ISL_STATUS_PROPS_DOWN)
        # gamma -> delta is down
        self.ensure_isl_props(
                neo4j_connect, isl_gamma_delta, ISL_STATUS_PROPS_DOWN)
        self.ensure_isl_props(
                neo4j_connect, isl_delta_gamma, ISL_STATUS_PROPS_HALF_UP)
        # beta <-> gamma is half up
        self.ensure_isl_props(
                neo4j_connect, isl_beta_gamma, ISL_STATUS_PROPS_HALF_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_gamma_beta, ISL_STATUS_PROPS_DOWN)

        self.assertTrue(
                share.feed_isl_discovery(isl_gamma_beta))
        # alpha <-> beta is down
        self.ensure_isl_props(
                neo4j_connect, isl_alpha_beta, ISL_STATUS_PROPS_HALF_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_beta_alpha, ISL_STATUS_PROPS_DOWN)
        # gamma -> delta is down
        self.ensure_isl_props(
                neo4j_connect, isl_gamma_delta, ISL_STATUS_PROPS_DOWN)
        self.ensure_isl_props(
                neo4j_connect, isl_delta_gamma, ISL_STATUS_PROPS_HALF_UP)
        # beta <-> gamma is up
        self.ensure_isl_props(
                neo4j_connect, isl_beta_gamma, ISL_STATUS_PROPS_UP)
        self.ensure_isl_props(
                neo4j_connect, isl_gamma_beta, ISL_STATUS_PROPS_UP)

    def test_isl_down_without_isl(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        isl_alpha_beta = model.InterSwitchLink(
                model.IslPathNode(sw_alpha, 2),
                model.IslPathNode(sw_beta, 2), None)
        isl_beta_alpha = isl_alpha_beta.reversed()

        self.assertTrue(make_switch_add(sw_alpha))
        self.assertTrue(make_isl_failed(isl_alpha_beta.source))

        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_alpha_beta)
        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_beta_alpha)

    def test_port_down_without_isl(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        isl_alpha_beta = model.InterSwitchLink(
                model.IslPathNode(sw_alpha, 2),
                model.IslPathNode(sw_beta, 2), None)
        isl_beta_alpha = isl_alpha_beta.reversed()

        self.assertTrue(make_switch_add(sw_alpha))
        self.assertTrue(make_port_down(isl_alpha_beta.source))

        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_alpha_beta)
        self.assertRaises(
                exc.DBRecordNotFound, isl_utils.fetch,
                neo4j_connect, isl_beta_alpha)

    def test_port_down_with_cost_as_string(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        isl_alpha_beta = model.InterSwitchLink(
                model.NetworkEndpoint(sw_alpha, 2),
                model.NetworkEndpoint(sw_beta, 2), None)
        self.assertTrue(share.feed_isl_discovery(isl_alpha_beta))

        with neo4j_connect.begin() as tx:
            isl_utils.set_props(tx, isl_alpha_beta, {"cost": "10"})

        self.assertTrue(make_port_down(isl_alpha_beta.source))

    def test_multi_isl_port_down(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        sw_gamma = share.make_datapath_id(3)

        isl_alpha_beta = model.InterSwitchLink(
            model.IslPathNode(sw_alpha, 2),
            model.IslPathNode(sw_beta, 2), None)
        isl_beta_alpha = isl_alpha_beta.reversed()
        isl_beta_gamma = model.InterSwitchLink(
            model.IslPathNode(sw_beta, 2),
            model.IslPathNode(sw_gamma, 3), None)
        isl_gamma_beta = isl_beta_gamma.reversed()

        for dpid in sw_alpha, sw_beta, sw_gamma:
            self.assertTrue(make_switch_add(dpid))

        self.assertTrue(share.feed_isl_discovery(isl_alpha_beta))
        self.assertTrue(share.feed_isl_discovery(isl_beta_alpha))
        self.assertTrue(share.feed_isl_discovery(isl_beta_gamma))
        self.assertTrue(share.feed_isl_discovery(isl_gamma_beta))

        self.assertTrue(make_port_down(isl_alpha_beta.dest))
        self.ensure_isl_props(
            neo4j_connect, isl_alpha_beta, ISL_STATUS_PROPS_HALF_UP)
        self.ensure_isl_props(
            neo4j_connect, isl_beta_alpha, ISL_STATUS_PROPS_DOWN)
        self.ensure_isl_props(
            neo4j_connect, isl_beta_gamma, ISL_STATUS_PROPS_DOWN)
        self.ensure_isl_props(
            neo4j_connect, isl_gamma_beta, ISL_STATUS_PROPS_HALF_UP)

    def test_cost_raise_on_port_down(self):
        self.setup_initial_data()
        self._cost_raise_on_port_down(10, 10010, 10010)
        self._cost_raise_on_port_down(10010, 10010, 10010)

        self._set_isl_cost('20')

        self._cost_raise_on_port_down(20, 10020, 10020)
        self._cost_raise_on_port_down(10020, 10020, 10020)

        self._set_isl_cost(0)

        self._cost_raise_on_port_down(0, 10000, 10000)
        self._cost_raise_on_port_down(10000, 10000, 10000)

    def test_cost_raise_on_port_down_without_link_props(self):
        self.setup_initial_data(make_link_props=False)
        self._cost_raise_on_port_down(None, 10000, 10000)

    def _cost_raise_on_port_down(self, initial, down, recover):
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint
        isl = model.InterSwitchLink(src_endpoint, dst_endpoint, None)

        self.ensure_isl_costs(
            (src_endpoint, initial),
            (dst_endpoint, initial))

        self.assertTrue(
            make_port_down(src_endpoint), 'Port DOWN command have failed')

        self.ensure_isl_costs(
            (src_endpoint, down),
            (dst_endpoint, down))

        self.assertTrue(
            share.feed_isl_discovery(isl),
            'ISL discovery command have failed')

        self.ensure_isl_costs(
            (src_endpoint, recover),
            (dst_endpoint, recover))

    def _set_isl_cost(self, value):
        with neo4j_connect.begin() as tx:
            isl = model.InterSwitchLink(
                self.src_endpoint, self.dst_endpoint, None)
            isl_utils.set_cost(tx, isl, value)
            isl_utils.set_cost(tx, isl.reversed(), value)

        with neo4j_connect.begin() as tx:
            props = {'cost': model.convert_integer(value)}
            self.ensure_isl_props(tx, isl, props)
            self.ensure_isl_props(tx, isl.reversed(), props)

    def test_no_cost_raise_on_isl_down(self):
        self.setup_initial_data()

        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint
        isl = model.InterSwitchLink(src_endpoint, dst_endpoint, None)

        self.ensure_isl_costs(
                (src_endpoint, 10),
                (dst_endpoint, 10))

        self.assertTrue(
                make_isl_failed(src_endpoint), 'Port DOWN command have failed')

        self.ensure_isl_costs(
                (src_endpoint, 10),
                (dst_endpoint, 10))

        self.assertTrue(
                share.feed_isl_discovery(isl), 'ISL discovery command have failed')

        self.ensure_isl_costs(
                (src_endpoint, 10),
                (dst_endpoint, 10))

    def test_moved_isl_should_be_marked(self):
        self.setup_initial_data()
        src_endpoint, dst_endpoint = self.src_endpoint, self.dst_endpoint
        forward = model.InterSwitchLink(src_endpoint, dst_endpoint, None)

        make_isl_moved(forward)
        self.ensure_isl_props(neo4j_connect, forward, ISL_STATUS_PROPS_MOVED)

    def test_isl_without_life_cycle_fields(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        isl_alpha_beta = model.InterSwitchLink(
                model.IslPathNode(sw_alpha, 2),
                model.IslPathNode(sw_beta, 2), None)

        self.assertTrue(make_switch_add(sw_alpha))
        self.assertTrue(make_switch_add(sw_beta))

        self.assertTrue(share.feed_isl_discovery(isl_alpha_beta))

        with neo4j_connect.begin() as tx:
            neo4j_connect.pull(isl_utils.fetch(tx, isl_alpha_beta))
            isl_utils.set_props(
                    tx, isl_alpha_beta,
                    {'time_create': None, 'time_modify': None})

        self.assertTrue(share.feed_isl_discovery(isl_alpha_beta))

    def test_time_update_on_isl_discovery(self):
        sw_alpha = share.make_datapath_id(1)
        sw_beta = share.make_datapath_id(2)
        isl_alpha_beta = model.InterSwitchLink(
                model.IslPathNode(sw_alpha, 2),
                model.IslPathNode(sw_beta, 2), None)

        self.assertTrue(make_switch_add(sw_alpha))
        self.assertTrue(make_switch_add(sw_beta))

        update_point_a = model.TimeProperty.now(milliseconds_precission=True)

        message = share.command(share.isl_info_payload(isl_alpha_beta))
        message['timestamp'] = update_point_a.as_java_timestamp()
        self.feed_service(message)

        recovered = model.TimeProperty.new_from_java_timestamp(message['timestamp'])
        self.assertEquals(update_point_a.value, recovered.value)

        with neo4j_connect.begin() as tx:
            isl = isl_utils.fetch(tx, isl_alpha_beta)
            neo4j_connect.pull(isl)

            db_ctime = model.TimeProperty.new_from_db(isl['time_create'])
            self.assertEqual(update_point_a.value, db_ctime.value)

            db_mtime = model.TimeProperty.new_from_db(isl['time_modify'])
            self.assertEqual(update_point_a.value, db_mtime.value)

        # one more update
        update_point_b = model.TimeProperty.now(milliseconds_precission=True)
        self.assertNotEqual(update_point_a.value, update_point_b.value)

        message['timestamp'] = update_point_b.as_java_timestamp()
        self.feed_service(message)
        with neo4j_connect.begin() as tx:
            isl = isl_utils.fetch(tx, isl_alpha_beta)
            neo4j_connect.pull(isl)

            db_ctime = model.TimeProperty.new_from_db(isl['time_create'])
            self.assertEqual(update_point_a.value, db_ctime.value)

            db_mtime = model.TimeProperty.new_from_db(isl['time_modify'])
            self.assertEqual(update_point_b.value, db_mtime.value)

    def ensure_isl_props(self, tx, isl, props):
        isl_record = isl_utils.fetch(tx, isl)
        neo4j_connect.pull(isl_record)
        for name in props:
            self.assertEqual(
                    props[name], isl_record[name],
                    "Invalid ISL's {!r} value ({!r})".format(name, isl_record))

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

        make_switch_add(src_endpoint.dpid)
        make_switch_add(dst_endpoint.dpid)

        if make_link_props:
            with neo4j_connect.begin() as tx:
                payload = {'cost': 10}
                inject_db_link_props(tx, src_endpoint, dst_endpoint, payload)
                inject_db_link_props(tx, dst_endpoint, src_endpoint, payload)

        make_isl_pair(src_endpoint, dst_endpoint)
