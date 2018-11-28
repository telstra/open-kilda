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
from topologylistener import config
from topologylistener import message_utils
from topologylistener.tests import share


class TestFlow(share.AbstractTest):
    flow_alpha = None

    def setUp(self):
        super(TestFlow, self).setUp()

        share.env.reset_kafka_producer()
        self.allow_all_features()

        # create flow
        request = self.load_flow_request('flow-create-request.json')
        request['correlation_id'] = self.make_correlation_id()
        payload = request['payload']
        for thread in payload['payload'].values():
            thread['flowid'] = '{}-alpha'.format(type(self).__name__)
        share.feed_message(request)

        self.flow_alpha = payload

    def test_switch_sync(self):
        sync_switches = self.extract_flow_path_switches(self.flow_alpha)
        cookies = self.extract_flow_cookies(self.flow_alpha)
        self.assertTrue(1 < len(sync_switches))
        sw = sync_switches[1]

        sync_request = share.switch_rules_sync(sw, rules=cookies)
        self.feed_service(share.command(sync_request))

        response = self.take_kafka_response(
            config.KAFKA_SPEAKER_FLOW_TOPIC, offset=1,
            expect_class=message_utils.MT_COMMAND)
        self.assertEqual(
            'org.openkilda.messaging.command.flow.BatchInstallRequest',
            response['clazz'])

        pending_cookies = set(cookies)
        for command in response['flow_commands']:
            pending_cookies.remove(command['cookie'])
        self.assertFalse(pending_cookies)

    @staticmethod
    def extract_flow_cookies(flow_data):
        return [
            flow_data['payload'][direction]['cookie']
            for direction in ('forward', 'reverse')]

    @staticmethod
    def extract_flow_path_switches(flow_data):
        forward_thread = flow_data['payload']['forward']
        return [x['switch_id'] for x in forward_thread['flowpath']['path']]
