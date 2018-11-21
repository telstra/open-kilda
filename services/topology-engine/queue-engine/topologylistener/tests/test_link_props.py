# Copyright 2018 Telstra Open Source
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
import time
import uuid

from topologylistener import exc
from topologylistener import config
from topologylistener import isl_utils
from topologylistener import link_props_utils
from topologylistener import message_utils
from topologylistener import messageclasses
from topologylistener import model
from topologylistener.tests import share


class Abstract(share.AbstractTest):
    endpoint_alpha = model.NetworkEndpoint(share.make_datapath_id(1), 4)
    endpoint_beta = model.NetworkEndpoint(share.make_datapath_id(2), 6)
    endpoint_gamma = model.NetworkEndpoint(share.make_datapath_id(3), 8)

    isl_alpha_beta = model.InterSwitchLink(
        model.IslPathNode(endpoint_alpha.dpid, endpoint_alpha.port),
        model.IslPathNode(endpoint_beta.dpid, endpoint_beta.port))
    isl_beta_alpha = isl_alpha_beta.reversed()

    def setUp(self):
        super(Abstract, self).setUp()

        for isl in (self.isl_alpha_beta, self.isl_beta_alpha):
            isl_info = share.isl_info_payload(isl)
            command = share.command(isl_info)
            self.assertTrue(messageclasses.MessageItem(command).handle())

    def take_response(self, **kwargs):
        return self.take_kafka_response(config.KAFKA_NORTHBOUND_TOPIC, **kwargs)

    def _ensure_props_match(self, link_props, props):
        with self.open_neo4j_session() as tx:
            persistent = link_props_utils.read(tx, link_props)
            self.assertEqual(props, persistent.props)

    def _put(self, subject):
        request = share.link_props_request(subject)
        payload = share.link_props_put_payload(request)
        self.feed_service(share.command(payload))
        return payload


class TestLinkProps01Clean(Abstract):
    def test_put(self):
        link_props = model.LinkProps(
            self.endpoint_beta, self.endpoint_gamma,
            props={'cost': 5, 'custom': 'test'})

        # to make time_create and time_modify different
        link_props.time_create = model.TimeProperty.new_from_java_timestamp(
            link_props.time_create.as_java_timestamp() - 100)

        # to make a difference in time fields, if incorrect values(not from
        # passed object) are used
        time.sleep(0.001)

        self._put(link_props)

        with self.open_neo4j_session() as tx:
            persistent = link_props_utils.read(tx, link_props)
            self.assertEquals(link_props, persistent)

        response = self.take_response()
        self.assertEqual(
            message_utils.MI_LINK_PROPS_RESPONSE, response['clazz'])
        self.assertIsNone(response['error'])

        encoded_link_props = share.link_props_request(link_props)
        encoded_link_props = json.loads(json.dumps(encoded_link_props))
        self.assertEqual(encoded_link_props, response['link_props'])

    def test_put_and_propagate(self):
        unique_value = str(uuid.uuid4())
        link_props = model.LinkProps(
            self.endpoint_alpha, self.endpoint_beta,
            props={'test': unique_value})
        self._put(link_props)

        with self.open_neo4j_session() as tx:
            persistent = link_props_utils.read(tx, link_props)
            self.assertEqual(link_props, persistent)

            isl_db = isl_utils.fetch(
                tx, model.InterSwitchLink.new_from_link_props(link_props))
            tx.graph.pull(isl_db)
            self.assertEqual(unique_value, isl_db['test'])

    def test_put_protected_field(self):
        link_props = model.LinkProps(
            self.endpoint_beta, self.endpoint_alpha,
            props={'latency': -2})
        self._put(link_props)

        response = self.take_response()
        self.assertEqual(
            message_utils.MI_LINK_PROPS_RESPONSE, response['clazz'])
        self.assertIsNone(response['link_props'])
        self.assertIsNotNone(response['error'])

    def test_put_incomplete(self):
        link_props = model.LinkProps(
            self.endpoint_beta, self.endpoint_gamma)
        link_props.source = model.NetworkEndpoint(
            self.endpoint_alpha.dpid, None)
        self._put(link_props)

        response = self.take_response()
        self.assertEqual(
            message_utils.MI_LINK_PROPS_RESPONSE, response['clazz'])
        self.assertIsNotNone(response['error'])
        self.assertIsNone(response['link_props'])


class TestLinkProps02Occupied(Abstract):
    def setUp(self):
        super(TestLinkProps02Occupied, self).setUp()

        alpha_beta = model.LinkProps(
            self.endpoint_alpha, self.endpoint_beta,
            props={'cost': '32', 'name': 'alpha-beta'})
        alpha_gamma = model.LinkProps(
            self.endpoint_alpha, self.endpoint_gamma,
            props={'cost': '96', 'name': 'alpha-gamma'})
        beta_gamma = model.LinkProps(
            self.endpoint_beta, self.endpoint_gamma,
            props={'cost': '64', 'name': 'beta-gamma'})

        self._put(alpha_beta)
        self._put(alpha_gamma)
        self._put(beta_gamma)

    def test_update(self):
        link_props = model.LinkProps(
            self.endpoint_alpha, self.endpoint_beta,
            props={'cost': 1, 'extra': 'new'})
        self._put(link_props)

        self._ensure_props_match(
            link_props, {'cost': 1, 'extra': 'new', 'name': 'alpha-beta'})

    def test_drop(self):
        lookup_mask = model.LinkProps(self.endpoint_alpha, self.endpoint_beta)

        with self.open_neo4j_session() as tx:
            isl_db = isl_utils.fetch(tx, model.InterSwitchLink(
                self.endpoint_alpha, self.endpoint_beta))
            tx.graph.pull(isl_db)
            self.assertEqual('alpha-beta', isl_db['name'])

        self._drop(lookup_mask)

        self._ensure_missing(lookup_mask)
        self._ensure_exists(
            model.LinkProps(self.endpoint_alpha, self.endpoint_gamma),
            model.LinkProps(self.endpoint_beta, self.endpoint_gamma))

        with self.open_neo4j_session() as tx:
            isl_db = isl_utils.fetch(tx, model.InterSwitchLink(
                self.endpoint_alpha, self.endpoint_beta))
            tx.graph.pull(isl_db)
            self.assertNotIn('name', isl_db)

    def test_drop_multi(self):
        any_endpoint = model.NetworkEndpoint(None, None)
        lookup_mask = model.LinkProps(self.endpoint_alpha, any_endpoint)
        self._drop(lookup_mask)

        self._ensure_missing(
            model.LinkProps(self.endpoint_alpha, self.endpoint_beta),
            model.LinkProps(self.endpoint_alpha, self.endpoint_gamma))
        self._ensure_exists(
            model.LinkProps(self.endpoint_beta, self.endpoint_gamma))

    def test_drop_reject(self):
        any_endpoint = model.NetworkEndpoint(None, None)
        lookup_mask = model.LinkProps(any_endpoint, any_endpoint)
        self._drop(lookup_mask)

        self._ensure_exists(
            model.LinkProps(self.endpoint_alpha, self.endpoint_beta),
            model.LinkProps(self.endpoint_alpha, self.endpoint_gamma),
            model.LinkProps(self.endpoint_beta, self.endpoint_gamma))

        response = self.take_response(
            expect_class=message_utils.MT_INFO_CHUNKED)
        self.assertIsNotNone(response)
        self.assertEqual(
            message_utils.MI_LINK_PROPS_RESPONSE, response['clazz'])
        self.assertIsNone(response['link_props'])
        self.assertIsNotNone(response['error'])

    def test_cypher_injection(self):
        link_props = model.LinkProps(
            self.endpoint_alpha, self.endpoint_beta,
            props={'`cost': 1})
        self._put(link_props)

        self._ensure_props_match(
            link_props, {'cost': 32, 'name': 'alpha-beta'})

    def _ensure_exists(self, *batch):
        with self.open_neo4j_session() as tx:
            for subject in batch:
                link_props_utils.read(tx, subject)

    def _ensure_missing(self, *batch):
        with self.open_neo4j_session() as tx:
            for subject in batch:
                try:
                    link_props_utils.read(tx, subject)
                    raise AssertionError(
                        'Record {} exist (must not exist)'.format(subject))
                except exc.DBRecordNotFound:
                    pass

    def _drop(self, subject):
        request = share.link_props_request(subject)
        payload = share.link_props_drop_payload(request)
        self.feed_service(share.command(payload))
        return payload
