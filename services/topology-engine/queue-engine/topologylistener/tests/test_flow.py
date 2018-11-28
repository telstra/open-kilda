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
import copy
import json
import pprint

from topologylistener import message_utils
from topologylistener import messageclasses
from topologylistener import model
from topologylistener.tests import share


class AbstractSpeakerFlowCommand(model.Abstract):
    clazz = model.Default(None)
    dpid = model.Default(None)
    cookie = model.Default(None)
    port_in = model.Default(None)
    port_out = model.Default(None)

    @classmethod
    def of_kafka_channel(cls, channel_data, **extra):
        channel_to_fields = {
            'switch_id': 'dpid',
            'output_port': 'port_out',
            'input_port': 'port_in',
            'cookie': 'cookie'}
        fields = model.grab_fields(channel_data, channel_to_fields)
        fields.update(extra)
        return cls(**fields)

    def as_tuple(self):
        return (
            self.clazz,
            self.dpid,
            self.cookie,
            self.port_in, self.port_out)

    def _sort_key(self):
        return self.as_tuple()


class AbstractSpeakerFlowInstallCommand(AbstractSpeakerFlowCommand):
    vlan_transit = model.Default(None)

    @classmethod
    def of_kafka_channel(cls, channel_data, **extra):
        fields = model.grab_fields(channel_data, {
            'transit_vlan_id': 'vlan_transit'})
        fields.update(extra)
        return super(AbstractSpeakerFlowInstallCommand, cls).of_kafka_channel(
            channel_data, **fields)

    def as_tuple(self):
        return super(AbstractSpeakerFlowInstallCommand, self).as_tuple() + (
            self.vlan_transit,)


class SpeakerFlowIngressCommand(AbstractSpeakerFlowInstallCommand):
    vlan_input = model.Default(None)

    @classmethod
    def of_kafka_channel(cls, channel_data, **extra):
        fields = model.grab_fields(
            channel_data, {'input_vlan_id': 'vlan_input'})
        fields.update(extra)
        return super(SpeakerFlowIngressCommand, cls).of_kafka_channel(
            channel_data, **fields)

    def __init__(self, **fields):
        super(AbstractSpeakerFlowCommand, self).__init__(
            clazz='org.openkilda.messaging.command.flow.InstallIngressFlow',
            **fields)

    def as_tuple(self):
        return super(SpeakerFlowIngressCommand, self).as_tuple() + (
            self.vlan_input, )


class SpeakerFlowTransitCommand(AbstractSpeakerFlowInstallCommand):
    def __init__(self, **fields):
        super(AbstractSpeakerFlowCommand, self).__init__(
            clazz='org.openkilda.messaging.command.flow.InstallTransitFlow',
            **fields)


class SpeakerFlowEgressCommand(AbstractSpeakerFlowInstallCommand):
    vlan_output = model.Default(None)

    @classmethod
    def of_kafka_channel(cls, channel_data, **extra):
        fields = model.grab_fields(
            channel_data, {'output_vlan_id': 'vlan_output'})
        fields.update(extra)
        return super(SpeakerFlowEgressCommand, cls).of_kafka_channel(
            channel_data, **fields)

    def __init__(self, **fields):
        super(AbstractSpeakerFlowCommand, self).__init__(
            clazz='org.openkilda.messaging.command.flow.InstallEgressFlow',
            **fields)

    def as_tuple(self):
        return super(SpeakerFlowEgressCommand, self).as_tuple() + (
            self.vlan_output, )


class SpeakerFlowRemoveCommand(AbstractSpeakerFlowCommand):
    vlan_input = model.Default(None)

    @classmethod
    def of_kafka_channel(cls, channel_data, **extra):
        fields = model.grab_fields(channel_data['criteria'], {
            'cookie': 'cookie',
            'in_port': 'port_in',
            'out_port': 'port_out',
            'in_vlan': 'vlan_input'})
        fields.update(extra)
        return super(SpeakerFlowRemoveCommand, cls).of_kafka_channel(
            channel_data, **fields)

    def __init__(self, **fields):
        # FIXME(surabujin): TE fill incorrect values in `port_out`
        # into FlowRemove command
        fields['port_out'] = None

        super(AbstractSpeakerFlowCommand, self).__init__(
            clazz='org.openkilda.messaging.command.flow.RemoveFlow',
            **fields)

    def as_tuple(self):
        return super(SpeakerFlowRemoveCommand, self).as_tuple() + (
            self.vlan_input, )


clazz_to_command = {
    'org.openkilda.messaging.command.flow.InstallIngressFlow':
        SpeakerFlowIngressCommand,
    'org.openkilda.messaging.command.flow.InstallTransitFlow':
        SpeakerFlowTransitCommand,
    'org.openkilda.messaging.command.flow.InstallEgressFlow':
        SpeakerFlowEgressCommand,
    'org.openkilda.messaging.command.flow.RemoveFlow': SpeakerFlowRemoveCommand}


class TestFlow(share.AbstractTest):
    def setUp(self):
        super(TestFlow, self).setUp()

        share.env.reset_kafka_producer()

        self.allow_all_features()

    def test_crud(self):
        request = self.load_flow_request('flow-create-request.json')

        # create
        request['correlation_id'] = self.make_correlation_id()
        create_request = copy.deepcopy(request)
        self.flow_create(create_request)

        # update
        request['correlation_id'] = self.make_correlation_id()
        request['payload']['operation'] = 'UPDATE'
        self.mangle_cookie(request, lambda cookie: cookie + 1)
        self.mangle_dst_port(request, lambda port: port + 1)
        update_request = copy.deepcopy(request)
        self.flow_update(update_request, create_request)

        # delete
        request['correlation_id'] = self.make_correlation_id()
        request['payload']['operation'] = 'DELETE'
        delete_request = copy.deepcopy(request)
        self.flow_delete(delete_request)

    def flow_create(self, create_request):
        flow_pair = create_request['payload']['payload']
        self.assertFalse(self.is_flow_exist(flow_pair['forward']['flowid']))
        self.assertTrue(share.feed_message(create_request))

        self.validate_db_flow(create_request)
        expected_messages = self.flow_create_expected_commands(create_request)
        self.validate_produced_commands(
            expected_messages, create_request['correlation_id'])

    def flow_update(self, update_request, create_request):
        flow_pair = update_request['payload']['payload']
        self.assertTrue(self.is_flow_exist(flow_pair['forward']['flowid']))
        self.assertTrue(share.feed_message(update_request))

        self.validate_db_flow(update_request)
        expected_commands = self.flow_create_expected_commands(update_request)

        expected_commands.update(self.flow_delete_expected_commands(
            create_request))
        self.validate_produced_commands(
            expected_commands, update_request['correlation_id'])

    def flow_delete(self, delete_request):
        flow_pair = delete_request['payload']['payload']
        flow_id = flow_pair['forward']['flowid']

        self.assertTrue(self.is_flow_exist(flow_id))
        self.assertTrue(share.feed_message(delete_request))
        self.assertFalse(self.is_flow_exist(flow_id))

        expect_commands = self.flow_delete_expected_commands(delete_request)
        self.validate_produced_commands(
            expect_commands, delete_request['correlation_id'])

    def validate_db_flow(self, request):
        flow_pair = request['payload']['payload']
        flow_forward = flow_pair['forward']

        flow_id = flow_forward['flowid']
        expected_endpoints = set(self.extract_flow_endpoints(flow_forward))
        with self.open_neo4j_session() as tx:
            for db_flow in self.fetch_db_flow(tx, flow_id):
                actual_endpoints = set(self.extract_flow_endpoints(db_flow))
                self.assertEqual(expected_endpoints, actual_endpoints)

    def validate_produced_commands(self, expected_commands, correlation_id):
        kafka_stream = self.kafka_backlog_stream()
        kafka_stream = self.stream_filter_by_key(
            kafka_stream, 'correlation_id', correlation_id)
        kafka_stream = self.stream_filter_by_key(
            kafka_stream, 'clazz', message_utils.MT_COMMAND)
        kafka_stream = self.stream_map(kafka_stream, lambda x: x['payload'])

        extra_commands = []
        for payload in kafka_stream:
            try:
                klass = clazz_to_command[payload['clazz']]
            except KeyError:
                extra_commands.append(payload)
                continue

            command = klass.of_kafka_channel(payload).as_tuple()
            if command in expected_commands:
                expected_commands.discard(command)
            else:
                extra_commands.append(payload)

        if extra_commands:
            message = 'Extra command messages have been produced'
            print(message)
            for idx, payload in enumerate(extra_commands):
                print('#{}: {}'.format(idx, pprint.pformat(payload)))
            raise AssertionError(message)

        if expected_commands:
            message = 'Expected command messages have not been produced'
            print(message)
            for idx, payload in enumerate(sorted(expected_commands)):
                print('#{}: {}'.format(idx, pprint.pformat(payload)))
            raise AssertionError(message)

    def is_flow_exist(self, flow_id):
        with self.open_neo4j_session() as tx:
            return bool(list(self.fetch_db_flow(tx, flow_id)))

    @staticmethod
    def fetch_db_flow(tx, flow_id):
        q = (
            'MATCH (:switch)-[f:flow]->(:switch)\n'
            'WHERE f.flowid=$flow_id\n'
            'RETURN f')
        p = {'flow_id': flow_id}

        cursor = tx.run(q, p)
        return (dict(x['f']) for x in cursor)

    @staticmethod
    def flow_create_expected_commands(request):
        flow_pair = request['payload']['payload']
        forward = flow_pair['forward']
        reverse = flow_pair['reverse']

        path_forward = forward['flowpath']['path']
        path_reverse = reverse['flowpath']['path']
        common_args_forward = {
            'cookie': forward['cookie'],
            'vlan_transit': forward['transit_vlan']
        }
        common_args_reverse = {
            'cookie': reverse['cookie'],
            'vlan_transit': reverse['transit_vlan']
        }

        return {
            # forward
            SpeakerFlowIngressCommand(
                dpid=forward['src_switch'],
                port_in=forward['src_port'],
                port_out=path_forward[0]['port_no'],
                vlan_input=forward['src_vlan'],
                **common_args_forward).as_tuple(),
            SpeakerFlowTransitCommand(
                dpid=path_forward[1]['switch_id'],
                port_in=path_forward[1]['port_no'],
                port_out=path_forward[2]['port_no'],
                **common_args_forward).as_tuple(),
            SpeakerFlowEgressCommand(
                dpid=forward['dst_switch'],
                port_in=path_forward[3]['port_no'],
                port_out=forward['dst_port'],
                vlan_output=forward['dst_vlan'],
                **common_args_forward).as_tuple(),
            # reverse
            SpeakerFlowIngressCommand(
                dpid=reverse['src_switch'],
                port_in=reverse['src_port'],
                port_out=path_reverse[0]['port_no'],
                vlan_input=reverse['src_vlan'],
                **common_args_reverse).as_tuple(),
            SpeakerFlowTransitCommand(
                dpid=path_reverse[1]['switch_id'],
                port_in=path_reverse[1]['port_no'],
                port_out=path_reverse[2]['port_no'],
                **common_args_reverse).as_tuple(),
            SpeakerFlowEgressCommand(
                dpid=reverse['dst_switch'],
                port_in=path_reverse[3]['port_no'],
                port_out=reverse['dst_port'],
                vlan_output=reverse['dst_vlan'],
                **common_args_reverse).as_tuple()}

    @staticmethod
    def flow_delete_expected_commands(request):
        flow_pairs = request['payload']['payload']
        forward = flow_pairs['forward']
        reverse = flow_pairs['reverse']

        path_forward = forward['flowpath']['path']
        path_reverse = reverse['flowpath']['path']

        common_args_forward = {'cookie': forward['cookie']}
        common_args_reverse = {'cookie': reverse['cookie']}
        return {
            # forward
            SpeakerFlowRemoveCommand(
                dpid=forward['src_switch'],
                port_in=forward['src_port'],
                port_out=path_forward[0]['port_no'],
                vlan_input=forward['src_vlan'],
                **common_args_forward).as_tuple(),
            SpeakerFlowRemoveCommand(
                dpid=path_forward[1]['switch_id'],
                port_in=path_forward[1]['port_no'],
                port_out=path_forward[2]['port_no'],
                vlan_input=forward['transit_vlan'],
                **common_args_forward).as_tuple(),
            SpeakerFlowRemoveCommand(
                dpid=forward['dst_switch'],
                port_in=path_forward[3]['port_no'],
                port_out=forward['dst_port'],
                vlan_input=forward['transit_vlan'],
                **common_args_forward).as_tuple(),
            # reverse
            SpeakerFlowRemoveCommand(
                dpid=reverse['src_switch'],
                port_in=reverse['src_port'],
                port_out=path_reverse[0]['port_no'],
                vlan_input=reverse['src_vlan'],
                **common_args_reverse).as_tuple(),
            SpeakerFlowRemoveCommand(
                dpid=path_reverse[1]['switch_id'],
                port_in=path_reverse[1]['port_no'],
                port_out=path_reverse[2]['port_no'],
                vlan_input=reverse['transit_vlan'],
                **common_args_reverse).as_tuple(),
            SpeakerFlowRemoveCommand(
                dpid=reverse['dst_switch'],
                port_in=path_reverse[3]['port_no'],
                port_out=reverse['dst_port'],
                vlan_input=reverse['transit_vlan'],
                **common_args_reverse).as_tuple()}

    @staticmethod
    def mangle_cookie(request, mangle):
        flow_pair = request['payload']['payload']
        for thread in flow_pair['forward'], flow_pair['reverse']:
            thread['cookie'] = mangle(thread['cookie'])

    @staticmethod
    def mangle_dst_port(request, mangle):
        flow_pair = request['payload']['payload']
        for thread in flow_pair['forward'], flow_pair['reverse']:
            thread['dst_port'] = mangle(thread['dst_port'])

    @staticmethod
    def extract_flow_endpoints(flow):
        return [
            flow['src_switch'],
            flow['dst_switch']]

    @staticmethod
    def stream_filter_by_key(stream, key, value):
        return (x for x in stream if x.get(key) == value)

    @staticmethod
    def stream_map(stream, action):
        return (action(x) for x in stream)

    @staticmethod
    def kafka_backlog_stream():
        return (
            json.loads(x.payload) for x in share.env.kafka_producer_backlog())
