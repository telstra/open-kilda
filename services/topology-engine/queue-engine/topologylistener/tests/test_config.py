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

from topologylistener import db
from topologylistener import messageclasses
from topologylistener.tests import share


def make_feature_toggle_request(payload):
    data = payload.copy()
    data['clazz'] = (
        'org.openkilda.messaging.command.system.FeatureToggleRequest')
    message = share.command(data)
    return messageclasses.MessageItem(message).handle()


def clean_neo4j_test_data(tx):
    tx.run('MATCH (c:config) DETACH DELETE c')


class TestConfig(share.AbstractTest):
    def setUp(self):
        super(TestConfig, self).setUp()
        with share.env.neo4j_connect.begin() as tx:
            clean_neo4j_test_data(tx)
        messageclasses.read_config()

    def test_update(self):
        features_request = share.feature_toggle_request(**{
            messageclasses.features_status_app_to_transport_map[
                messageclasses.FEATURE_REROUTE_ON_ISL_DISCOVERY]: False})
        self.assertTrue(share.feed_message(share.command(features_request)))

        with share.env.neo4j_connect.begin() as tx:
            cursor = tx.run(
                    'MATCH (c:config {name: "config"}) RETURN c LIMIT 2')
            db_config = db.fetch_one(cursor)['c']
            self.assertEqual(
                    False,
                    db_config[
                        messageclasses.features_status_app_to_transport_map[
                            messageclasses.FEATURE_REROUTE_ON_ISL_DISCOVERY]])

    def test_existing(self):
        with share.env.neo4j_connect.begin() as tx:
            q = 'CREATE (c:config) SET c = $data'
            p = {
                'data': {
                    'name': 'config',
                    'reroute_on_isl_discovery': False,
                    'unpush_flow': False,
                    'sync_rules': False,
                    'push_flow': False,
                    'delete_flow': False,
                    'create_flow': False,
                    'update_flow': False}}
            tx.run(q, p)

        messageclasses.read_config()
        self.assertFalse(
                messageclasses.features_status[
                    messageclasses.FEATURE_REROUTE_ON_ISL_DISCOVERY])
