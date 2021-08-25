#  Copyright 2021 Telstra Open Source
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

import collections
import json
import itertools
import hashlib

import mysql.connector.errorcode as mysql_errcode
import mysql.connector.errors as mysql_errors
import ndjson
import pyorient

from . import mapper
from . import model
from . import tools


def ndjson_to_mysql(mysql_client, stream_in):
    stream = _ndjson_stream(stream_in)
    stream = _decode_dump_records(stream)
    with _StatusOutput('MySQL push') as output:
        manager = _MysqlPushManager(mysql_client, output)
        for record in stream:
            _push(manager, record.tag, record.entry)
            output.flush()


def ndjson_to_orientdb(orient_client, stream_in):
    stream = _ndjson_stream(stream_in)
    stream = _decode_dump_records(stream)
    with _StatusOutput('OrientDB push') as output:
        manager = _OrientDbPushMangar(orient_client, output)
        for record in stream:
            _push(manager, record.tag, record.entry)
            output.flush()


def _push(manager, tag, entry):
    if tag == model.FLOW_EVENT_TAG.tag:
        manager.push_flow_event(entry)
    elif tag == model.PORT_EVENT_TAG.tag:
        manager.push_port_event(entry)
    else:
        raise ValueError(
            'Unknown type tag {!r}, can\'t process entry {}'.format(
                tag, entry))


def _ndjson_stream(stream_in):
    reader = ndjson.reader(stream_in)
    tag = model.TypeTag(None)  # dummy tag
    for entry in reader:
        if len(entry) == 1:  # possible type tag
            try:
                tag = mapper.type_tag_of_dump(entry)
                continue
            except ValueError:
                pass
        yield _StreamEntry(tag.tag, entry)


def _decode_dump_records(stream):
    for tag, dump_entry in stream:
        if tag == model.FLOW_EVENT_TAG.tag:
            entry = mapper.flow_event_of_dump(dump_entry)
        elif tag == model.PORT_EVENT_TAG.tag:
            entry = mapper.port_event_of_dump(dump_entry)
        else:
            raise ValueError('Unsupported type tag {!r} (record: {!r}'.format(
                tag, dump_entry))
        yield _StreamEntry(tag, entry)


class _EntityHandler:
    def push(self, entity):
        raise NotImplementedError


class _MysqlEntityHandler(_EntityHandler):
    def __init__(self, client, output):
        self._client = client
        self._output = output

    def push(self, entity):
        try:
            self._handle(entity)
            self._client.commit()
            self._output.record_success()
        except mysql_errors.IntegrityError as e:
            if e.errno != mysql_errcode.ER_DUP_ENTRY:
                raise
            self._client.rollback()
            self._output.record_skip()

    def _handle(self, entity):
        raise NotImplementedError


class _FlowEventMysqlEntityHandler(_MysqlEntityHandler):
    def __init__(self, client, output):
        super().__init__(client, _FlowStatsOutputAdapter(output))

    def _handle(self, flow_event):
        cursor = self._client.cursor()
        unstructured = {
            'actor': flow_event.actor,
            'details': flow_event.details
        }
        _filter_out_dict_none_values(unstructured)
        args = (
            flow_event.event_time,
            flow_event.flow_id,
            flow_event.event_id,
            self._new_event_id_unique_key(flow_event.event_id),
            flow_event.event_name,
            json.dumps(unstructured),
            flow_event.time_create, flow_event.time_modify)
        cursor.execute(
            'INSERT INTO flow_event '
            '(event_time, flow_id, task_id, task_id_unique_key, `action`, '
            'unstructured, time_create, time_modify) '
            'VALUES (%s, %s, %s, %s, %s, %s, %s, %s)', params=args)

        record_id = cursor.lastrowid
        for entry in flow_event.actions:
            self._handle_action(record_id, entry)
        for entry in flow_event.dumps:
            self._handle_dump(record_id, entry)

    def _handle_action(self, record_id, action):
        cursor = self._client.cursor()
        args = (
            record_id,
            action.action_time, action.action_name,
            action.details,
            action.time_create, action.time_modify)
        cursor.execute(
            'INSERT INTO flow_event_action '
            '(flow_event_id, event_time, `action`, details, time_create, '
            'time_modify) '
            'VALUES (%s, %s, %s, %s, %s, %s)', params=args)

    def _handle_dump(self, record_id, entry):
        cursor = self._client.cursor()
        unstructured = {
            'flow_id': entry.flow_id,
            'bandwidth': entry.bandwidth,
            'ignore_bandwidth': entry.ignore_bandwidth,
            'forward_cookie': entry.a_to_z_cookie,
            'reverse_cookie': entry.z_to_a_cookie,
            'source_switch': entry.a_end_switch,
            'source_port': entry.a_end_port,
            'source_vlan': entry.a_end_vlan,
            'source_inner_vlan': entry.a_end_inner_vlan,
            'destination_switch': entry.z_end_switch,
            'destination_port': entry.z_end_port,
            'destination_vlan': entry.z_end_vlan,
            'destination_inner_vlan': entry.z_end_inner_vlan,
            'forward_meter_id': entry.a_to_z_meter_id,
            'forward_path': entry.a_to_z_path,
            'forward_status': entry.a_to_z_status,
            'reverse_meter_id': entry.z_to_a_meter_id,
            'reverse_path': entry.z_to_a_path,
            'reverse_status': entry.z_to_a_status,
            'diverse_group_id': entry.diverse_group_id,
            'affinity_group_id': entry.affinity_group_id,
            'allocate_protected_path': entry.allocate_protected_path,
            'pinned': entry.pinned,
            'periodic_pings': entry.periodic_ping,
            'encapsulation_type': entry.encapsulation_type,
            'path_computation_strategy':
                entry.path_computation_strategy,
            'max_latency': entry.max_latency,
            'loop_switch_id': entry.loop_switch
        }
        _filter_out_dict_none_values(unstructured)
        _apply_dict_optional_value_action(
            unstructured, lambda x: x.upper(),
            'forward_status', 'reverse_status', 'encapsulation_type',
            'path_computation_strategy')

        args = (
            record_id,
            entry.kind, json.dumps(unstructured),
            entry.time_create, entry.time_modify)
        cursor.execute(
            'INSERT INTO flow_event_dump '
            '(flow_event_id, kind, unstructured, time_create, time_modify) '
            'VALUES (%s, %s, %s, %s, %s)',
            params=args)

    @staticmethod
    def _new_event_id_unique_key(value):
        hash = hashlib.sha256()
        hash.update(value.encode('utf-8'))
        return '{}:{:x}:sha256'.format(hash.hexdigest(), len(value))


class _PortEventMysqlEntityHandler(_MysqlEntityHandler):
    def __init__(self, client, output):
        super().__init__(client, _PortStatsOutputAdapter(output))

    def _handle(self, port_event):
        cursor = self._client.cursor()
        unstructured = {
            'up_events_count': port_event.up_count,
            'down_events_count': port_event.down_count}
        _filter_out_dict_none_values(unstructured)
        args = (
            port_event.event_id,
            port_event.event_time,
            port_event.switch_id, port_event.port_number,
            port_event.event_kind,
            json.dumps(unstructured),
            port_event.time_create, port_event.time_modify)
        cursor.execute(
            'INSERT INTO port_event '
            '(id, event_time, switch_id, port_number, `event`, unstructured, '
            'time_create, time_modify) '
            'VALUES (%s, %s, %s, %s, %s, %s, %s, %s)', params=args)


class _OrientDbEntityHandler(_EntityHandler):
    def __init__(self, client, clusters, output):
        self._client = client
        self._clusters = clusters
        self._output = output

    def push(self, entity):
        tx = self._client.tx_commit()
        # tx.begin()  # transactions are not working
        try:
            self._handle(tx, entity)
            # tx.commit()
            self._output.record_success()
        except pyorient.PyOrientORecordDuplicatedException:
            self._output.record_skip()
            # tx.rollback()
        except Exception:
            # tx.rollback()
            raise

    def _handle(self, tx, entity):
        raise NotImplementedError

    def _create_record(self, tx, orient_class, payload):
        #cluster = self._clusters.get_cluster_for_class(orient_class)
        #record = {'@' + orient_class: payload}
        #print('{} cluster-id: {}'.format(orient_class, cluster))
        #create_result = self._client.record_create(cluster, record)
        #tx.attach(create_result)

        fields = []
        for name, value in payload.items():
            if value is None:
                continue
            if isinstance(value, str):
                value = '"' + self._escape_string(value) + '"'
            fields.append('{} = {}'.format(name, value))
        q = 'INSERT INTO {} SET {}'.format(orient_class, ', '.join(fields))
        self._client.command(q)

    @staticmethod
    def _escape_string(value):
        for src, dst in (
                ('\\', '\\\\'),
                ('"', '\\"')):
            value = value.replace(src, dst)
        return value


class _FlowEventOrientDbEntityHandler(_OrientDbEntityHandler):
    def __init__(self, client, clusters, output):
        super().__init__(client, clusters, _FlowStatsOutputAdapter(output))

    def _handle(self, tx, flow_event):
        self._create_record(tx, 'flow_event', {
            'flow_id': flow_event.flow_id,
            'timestamp': mapper.datetime_to_java_time(flow_event.event_time),
            'actor': flow_event.actor,
            'action': flow_event.event_name,
            'task_id': flow_event.event_id,
            'details': flow_event.details,
            'time_create': mapper.datetime_to_string(flow_event.time_create),
            'time_modify': mapper.datetime_to_string(flow_event.time_modify)})

        try:
            for entry in flow_event.actions:
                self._handle_action(tx, flow_event.event_id, entry)

            for entry in flow_event.dumps:
                self._handle_dump(tx, flow_event.event_id, entry)
        except Exception:
            self._revert(flow_event.event_id)
            raise

    def _handle_action(self, tx, event_id, action):
        self._create_record(tx, 'flow_history', {
            'timestamp': mapper.datetime_to_java_time(action.action_time),
            'action': action.action_name,
            'task_id': event_id,
            'details': action.details,
            'time_create': mapper.datetime_to_string(action.time_create),
            'time_modify': mapper.datetime_to_string(action.time_modify)
        })

    def _handle_dump(self, tx, event_id, dump):
        record = {
            'task_id': event_id,
            'flow_id': dump.flow_id,
            'type': dump.kind,
            'bandwidth': dump.bandwidth,
            'ignoreBandwidth': dump.ignore_bandwidth,
            'forward_cookie': dump.a_to_z_cookie,
            'reverse_cookie': dump.z_to_a_cookie,
            'src_switch': dump.a_end_switch,
            'dst_switch': dump.z_end_switch,
            'src_port': dump.a_end_port,
            'dst_port': dump.z_end_port,
            'src_vlan': dump.a_end_vlan,
            'dst_vlan': dump.z_end_vlan,
            'src_inner_vlan': dump.a_end_inner_vlan,
            'dst_inner_vlan': dump.z_end_inner_vlan,
            'forward_meter_id': dump.a_to_z_meter_id,
            'reverse_meter_id': dump.z_to_a_meter_id,
            'diverse_group_id': dump.diverse_group_id,
            'affinity_group_id': dump.affinity_group_id,
            'forward_path': dump.a_to_z_path,
            'reverse_path': dump.z_to_a_path,
            'forward_status': dump.a_to_z_status,
            'reverse_status': dump.z_to_a_status,
            'allocate_protected_path': dump.allocate_protected_path,
            'pinned': dump.pinned,
            'periodic_pings': dump.periodic_ping,
            'encapsulation_type': dump.encapsulation_type,
            'path_computation_strategy': dump.path_computation_strategy,
            'max_latency': dump.max_latency,
            'loop_switch_id': dump.loop_switch,
            'time_create': mapper.datetime_to_string(dump.time_create),
            'time_modify': mapper.datetime_to_string(dump.time_modify)
        }
        _filter_out_dict_none_values(record)
        _apply_dict_optional_value_action(
            record, lambda x: x.upper(),
            'forward_status', 'reverse_status', 'encapsulation_type',
            'path_computation_strategy')

        self._create_record(tx, 'flow_dump', record)

    def _revert(self, event_id):
        self._client.command(
            'DELETE FROM flow_dump WHERE task_id = "{}"'.format(event_id))
        self._client.command(
            'DELETE FROM flow_history WHERE task_id = "{}"'.format(event_id))
        self._client.command(
            'DELETE FROM flow_event WHERE task_id = "{}"'.format(event_id))


class _PortEventOrientDbEntityHandler(_OrientDbEntityHandler):
    def __init__(self, client, clusters, output):
        super().__init__(client, clusters, _PortStatsOutputAdapter(output))

    def _handle(self, tx, port_event):
        self._create_record(tx, 'port_history', {
            'id': port_event.event_id,
            'switch_id': port_event.switch_id,
            'port_number': port_event.port_number,
            'event': port_event.event_kind,
            'time': mapper.datetime_to_java_time(port_event.event_time),
            'up_count': port_event.up_count,
            'down_count': port_event.down_count,
            'time_create': mapper.datetime_to_string(
                port_event.time_create),
            'time_modify': mapper.datetime_to_string(
                port_event.time_modify)})


class _PushManager:
    def __init__(
            self,
            flow_event_handler: _EntityHandler,
            port_event_handler: _EntityHandler):
        self._flow_event_handler = flow_event_handler
        self._port_event_handler = port_event_handler

    def push_flow_event(self, flow_event):
        self._flow_event_handler.push(flow_event)

    def push_port_event(self, port_event):
        self._port_event_handler.push(port_event)


class _MysqlPushManager(_PushManager):
    def __init__(self, client, output):
        super().__init__(
            _FlowEventMysqlEntityHandler(client, output),
            _PortEventMysqlEntityHandler(client, output))


class _OrientDbPushMangar(_PushManager):
    def __init__(self, client, output):
        clusters = _OrientDBClusterInfo(client)
        super().__init__(
            _FlowEventOrientDbEntityHandler(client, clusters, output),
            _PortEventOrientDbEntityHandler(client, clusters, output))


class _OrientDBClusterInfo:
    def __init__(self, client):
        clusters = collections.defaultdict(list)
        for entry in client.clusters:
            name = entry.name.decode('utf-8')
            orient_class, dummy, seq_number = name.rpartition('_')
            if not dummy:
                continue
            if not seq_number.isdigit():
                continue
            clusters[orient_class].append(entry.id)
        self._clusters_set_by_class = {
            k: itertools.cycle(tuple(v)) for k, v in clusters.items()}

    def get_cluster_for_class(self, name):
        return next(self._clusters_set_by_class[name])


_StreamEntry = collections.namedtuple('_StreamEntry', ('tag', 'entry'))


class _StatusOutput(tools.StatusOutputBase):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self._flow_success_count = 0
        self._flow_skip_count = 0
        self._port_success_count = 0
        self._port_skip_count = 0

    def record_flow_success(self):
        self._flow_success_count += 1

    def record_flow_skip(self):
        self._flow_skip_count += 1

    def record_port_success(self):
        self._port_success_count += 1

    def record_port_skip(self):
        self._port_skip_count += 1

    def _format_message(self):
        chunks = super()._format_message()
        chunks.append('processed ')
        payload_offset = len(chunks)
        for kind, success, skipped in (
                ('flow', self._flow_success_count, self._flow_skip_count),
                ('port', self._port_success_count, self._port_skip_count)):
            if payload_offset != len(chunks):
                chunks.append(', ')
            chunks.append(success)
            if skipped:
                chunks.append('({} skipped)'.format(skipped))
            chunks.append(' {} records'.format(kind))
        return chunks


class _FlowStatsOutputAdapter:
    def __init__(self, output):
        self.output = output

    def record_success(self):
        self.output.record_flow_success()

    def record_skip(self):
        self.output.record_flow_skip()


class _PortStatsOutputAdapter:
    def __init__(self, output):
        self.output = output

    def record_success(self):
        self.output.record_port_success()

    def record_skip(self):
        self.output.record_port_skip()


def _filter_out_dict_none_values(container):
    to_del = set()
    for name, value in container.items():
        if value is None:
            to_del.add(name)
    for name in to_del:
        del container[name]
    return container


def _apply_dict_optional_value_action(container, action, *keys):
    for name in keys:
        try:
            value = container[name]
        except KeyError:
            continue
        if value is None:
            continue
        container[name] = action(value)
