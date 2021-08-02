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

import datetime
import itertools
import json
import operator

from . import model


# -- flow event --

def flow_event_to_dump(flow_event):
    return {
        'event_id': flow_event.event_id,
        'event_time': datetime_to_string(flow_event.event_time),
        'flow_id': flow_event.flow_id,
        'event_name': flow_event.event_name,
        'actor': flow_event.actor,
        'details': flow_event.details,

        'actions': [_flow_event_action_to_dump(x) for x in flow_event.actions],
        'dumps': [_flow_event_dump_to_dump(x) for x in flow_event.dumps],

        'time_create': datetime_to_string(flow_event.time_create),
        'time_modify': datetime_to_string(flow_event.time_modify)
    }


def flow_event_of_orient(orient_entry, actions, dumps):
    args = [
        orient_entry.task_id,
        datetime_of_java_time(orient_entry.timestamp),
        orient_entry.flow_id,
        orient_entry.action
    ]
    kwargs = _get_generic_orient_fields(orient_entry)
    kwargs.update(_get_optional_attrs(orient_entry, 'actor', 'details'))

    kwargs['actions'] = [_flow_event_action_of_orient(x) for x in actions]
    kwargs['actions'].sort(key=lambda x: x.action_time)
    kwargs['dumps'] = [_flow_event_dump_of_orient(x) for x in dumps]
    kwargs['dumps'].sort(
        key=lambda x: x.time_create if x.time_create is not None else 0)

    return model.FlowEvent(*args, **kwargs)


def flow_event_of_mysql(mysql_row, actions, dumps):
    entry = dict(mysql_row)
    (
        mysql_row_id, flow_id, event_id, event_name, unstructured, event_time
    ) = _extract_dict_values(
        entry, 'id', 'flow_id', 'task_id', 'action', 'unstructured',
        'event_time')
    unstructured = json.loads(unstructured)
    event_time = datetime_force_timezone(event_time)

    kwargs = _get_generic_mysql_fields(mysql_row)
    kwargs.update(_get_optional_keys(unstructured, 'actor', 'details'))
    kwargs['actions'] = [_flow_event_action_of_mysql(x) for x in actions]
    kwargs['dumps'] = [_flow_event_dump_of_mysql(x) for x in dumps]
    return model.FlowEvent(event_id, event_time, flow_id, event_name, **kwargs)


def flow_event_of_dump(dump_entry):
    source = dump_entry.copy()
    (
        event_id, event_time, flow_id, event_name, actor, details,
        actions, dumps,
        time_create, time_modify
    ) = _extract_dict_values(
        source, 'event_id', 'event_time', 'flow_id', 'event_name', 'actor',
        'details', 'actions', 'dumps', 'time_create', 'time_modify',
        allow_extra=False)
    event_time = datetime_of_string(event_time)
    time_create = datetime_of_string(time_create)
    time_modify = datetime_of_string(time_modify)

    actions = [_flow_event_action_of_dump(x) for x in actions]
    dumps = [_flow_event_dump_of_dump(x) for x in dumps]

    return model.FlowEvent(
        event_id, event_time, flow_id, event_name, actor=actor,
        details=details, actions=actions, dumps=dumps, time_create=time_create,
        time_modify=time_modify)


# -- flow event action --

def _flow_event_action_to_dump(action):
    return {
        'action_time': datetime_to_string(action.action_time),
        'action_name': action.action_name,
        'details': action.details,
        'time_create': datetime_to_string(action.time_create),
        'time_modify': datetime_to_string(action.time_modify)
    }


def _flow_event_action_of_orient(orient_entry):
    args = [
        datetime_of_java_time(orient_entry.timestamp),
        orient_entry.action]
    kwargs = _get_generic_orient_fields(orient_entry)
    kwargs.update(_get_optional_attrs(orient_entry, 'details'))
    return model.FlowEventAction(*args, **kwargs)


def _flow_event_action_of_mysql(mysql_row):
    entry = dict(mysql_row)
    action_time, action_name, details = _extract_dict_values(
        entry, 'event_time', 'action', 'details')
    action_time = datetime_force_timezone(action_time)
    kwargs = _get_generic_mysql_fields(mysql_row)
    return model.FlowEventAction(
        action_time, action_name, details=details, **kwargs)


def _flow_event_action_of_dump(dump_entry):
    source = dump_entry.copy()
    (
        action_time, action_name, details, time_create, time_modify
    ) = _extract_dict_values(
        source, 'action_time', 'action_name', 'details',
        'time_create', 'time_modify', allow_extra=False)
    action_time = datetime_of_string(action_time)
    time_create = datetime_of_string(time_create)
    time_modify = datetime_of_string(time_modify)
    return model.FlowEventAction(
        action_time, action_name, details,
        time_create=time_create, time_modify=time_modify)


# -- flow event dump --

def _flow_event_dump_to_dump(dump):
    return {
        'kind': dump.kind,
        'flow_id': dump.flow_id,
        'bandwidth': dump.bandwidth,
        'max_latency': dump.max_latency,
        'ignore_bandwidth': dump.ignore_bandwidth,
        'allocate_protected_path': dump.allocate_protected_path,
        'encapsulation_type': dump.encapsulation_type,
        'path_computation_strategy': dump.path_computation_strategy,

        'a_to_z_cookie': dump.a_to_z_cookie,
        'z_to_z_cookie': dump.z_to_a_cookie,

        'a_end_switch': dump.a_end_switch,
        'a_end_port': dump.a_end_port,
        'a_end_vlan': dump.a_end_vlan,
        'a_end_inner_vlan': dump.a_end_inner_vlan,

        'z_end_switch': dump.z_end_switch,
        'z_end_port': dump.z_end_port,
        'z_end_vlan': dump.z_end_vlan,
        'z_end_inner_vlan': dump.z_end_inner_vlan,

        'a_to_z_path': dump.a_to_z_path,
        'a_to_z_status': dump.a_to_z_status,

        'z_to_a_path': dump.z_to_a_path,
        'z_to_a_status': dump.z_to_a_status,

        'pinned': dump.pinned,
        'periodic_ping': dump.periodic_ping,

        'group_id': dump.group_id,
        'loop_switch': dump.loop_switch,

        'a_to_z_meter_id': dump.a_to_z_meter_id,
        'z_to_a_meter_id': dump.z_to_a_meter_id,

        'time_create': datetime_to_string(dump.time_create),
        'time_modify': datetime_to_string(dump.time_modify)
    }


def _flow_event_dump_of_orient(orient_entry):
    kind = getattr(orient_entry, 'type')
    flow_id = orient_entry.flow_id
    bandwidth = orient_entry.bandwidth
    max_latency = getattr(orient_entry, 'max_latency', None)
    ignore_bandwidth = getattr(orient_entry, 'ignoreBandwidth')
    allocate_protected_path = getattr(
        orient_entry, 'allocate_protected_path', False)
    encapsulation_type = getattr(orient_entry, 'encapsulation_type', None)
    path_computation_strategy = getattr(
        orient_entry, 'path_computation_strategy', None)
    a_to_z_cookie = orient_entry.forward_cookie
    z_to_a_cookie = orient_entry.reverse_cookie
    a_end_switch = orient_entry.src_switch
    a_end_port = orient_entry.src_port
    a_end_vlan = orient_entry.src_vlan
    a_end_inner_vlan = getattr(orient_entry, 'src_inner_vlan', 0)
    z_end_switch = orient_entry.dst_switch
    z_end_port = orient_entry.dst_port
    z_end_vlan = orient_entry.dst_vlan
    z_end_inner_vlan = getattr(orient_entry, 'dst_inner_vlan', 0)
    a_to_z_path = getattr(orient_entry, 'forward_path', None)
    a_to_z_status = getattr(orient_entry, 'forward_status', None)
    z_to_a_path = getattr(orient_entry, 'reverse_path', None)
    z_to_a_status = getattr(orient_entry, 'reverse_status', None)

    (
        encapsulation_type, path_computation_strategy, a_to_z_status,
        z_to_a_status
    ) = _apply_action_if_not_none(
        lambda x: x.lower(),
        encapsulation_type, path_computation_strategy, a_to_z_status,
        z_to_a_status)

    kwargs = _get_generic_orient_fields(orient_entry)
    kwargs.update(_get_optional_attrs(
        orient_entry,
        'pinned', 'periodic_ping', 'group_id', loop_switch='loop_switch_id',
        a_to_z_meter_id='forward_meter_id', z_to_a_meter_id='reverse_meter_id'))
    return model.FlowEventDump(
        kind, flow_id, bandwidth, max_latency, ignore_bandwidth,
        allocate_protected_path, encapsulation_type, path_computation_strategy,
        a_to_z_cookie, z_to_a_cookie, a_end_switch, a_end_port, a_end_vlan,
        a_end_inner_vlan, z_end_switch, z_end_port, z_end_vlan,
        z_end_inner_vlan, a_to_z_path, a_to_z_status, z_to_a_path,
        z_to_a_status, **kwargs)


def _flow_event_dump_of_mysql(mysql_row):
    entry = dict(mysql_row)
    kind, unstructured = _extract_dict_values(entry, 'kind', 'unstructured')

    unstructured = json.loads(unstructured)
    (
        flow_id, bandwidth, max_latency, ignore_bandwidth,
        allocate_protected_path, encapsulation_type, path_computation_strategy,
        a_to_z_cookie, z_to_a_cookie, a_end_switch, a_end_port, a_end_vlan,
        a_end_inner_vlan, z_end_switch, z_end_port, z_end_vlan,
        z_end_inner_vlan, a_to_z_path, a_to_z_status, z_to_a_path,
        z_to_a_status,
    ) = _extract_dict_values(
        unstructured, 'flow_id', 'bandwidth',
        'max_latency', 'ignore_bandwidth', 'allocate_protected_path',
        'encapsulation_type', 'path_computation_strategy', 'forward_cookie',
        'reverse_cookie', 'source_switch', 'source_port', 'source_vlan',
        'source_inner_vlan', 'destination_switch', 'destination_port',
        'destination_vlan', 'destination_inner_vlan', 'forward_path',
        'forward_status', 'reverse_path', 'reverse_status')
    (
        encapsulation_type, path_computation_strategy, a_to_z_status,
        z_to_a_status
    ) = _apply_action_if_not_none(
        lambda x: x.lower(),
        encapsulation_type, path_computation_strategy, a_to_z_status,
        z_to_a_status)

    kwargs = _get_generic_mysql_fields(mysql_row)
    kwargs.update(_get_optional_keys(
        unstructured, 'pinned', 'group_id', 'loop_switch',
        periodic_ping='periodic_pings', a_to_z_meter_id='forward_meter_id',
        z_to_a_meter_id='reverse_meter_id'))
    return model.FlowEventDump(
        kind, flow_id, bandwidth, max_latency, ignore_bandwidth,
        allocate_protected_path, encapsulation_type, path_computation_strategy,
        a_to_z_cookie, z_to_a_cookie, a_end_switch, a_end_port, a_end_vlan,
        a_end_inner_vlan, z_end_switch, z_end_port, z_end_vlan,
        z_end_inner_vlan,
        a_to_z_path, a_to_z_status, z_to_a_path, z_to_a_status, **kwargs)


def _flow_event_dump_of_dump(dump_entry):
    source = dump_entry.copy()
    (
        kind, flow_id, bandwidth, max_latency, ignore_bandwidth,
        allocate_protected_path, encapsulation_type,
        path_computation_strategy, a_to_z_cookie, z_to_z_cookie,
        a_end_switch, a_end_port, a_end_vlan, a_end_inner_vlan,
        z_end_switch, z_end_port, z_end_vlan, z_end_inner_vlan,
        a_to_z_meter_id, a_to_z_path, a_to_z_status, z_to_a_meter_id,
        z_to_a_path, z_to_a_status, pinned, periodic_ping, group_id,
        loop_switch, time_create, time_modify
    ) = _extract_dict_values(
        source, 'kind', 'flow_id', 'bandwidth', 'max_latency',
        'ignore_bandwidth', 'allocate_protected_path',
        'encapsulation_type', 'path_computation_strategy', 'a_to_z_cookie',
        'z_to_z_cookie', 'a_end_switch', 'a_end_port', 'a_end_vlan',
        'a_end_inner_vlan', 'z_end_switch', 'z_end_port', 'z_end_vlan',
        'z_end_inner_vlan', 'a_to_z_meter_id', 'a_to_z_path', 'a_to_z_status',
        'z_to_a_meter_id', 'z_to_a_path', 'z_to_a_status', 'pinned',
        'periodic_ping', 'group_id', 'loop_switch', 'time_create',
        'time_modify',
        allow_extra=False)

    time_create = datetime_of_string(time_create)
    time_modify = datetime_of_string(time_modify)

    return model.FlowEventDump(
        kind, flow_id, bandwidth, max_latency, ignore_bandwidth,
        allocate_protected_path, encapsulation_type,
        path_computation_strategy, a_to_z_cookie, z_to_z_cookie, a_end_switch,
        a_end_port, a_end_vlan, a_end_inner_vlan, z_end_switch, z_end_port,
        z_end_vlan, z_end_inner_vlan, a_to_z_path, a_to_z_status,
        z_to_a_path, z_to_a_status,
        pinned=pinned, periodic_ping=periodic_ping,
        group_id=group_id, loop_switch=loop_switch, a_to_z_meter_id=a_to_z_meter_id, z_to_a_meter_id=z_to_a_meter_id, time_create=time_create,
        time_modify=time_modify)


# -- port event --

def port_event_to_dump(port_event):
    return {
        'event_id': port_event.event_id,
        'event_time': datetime_to_string(port_event.event_time),
        'switch_id': port_event.switch_id,
        'port_number': port_event.port_number,
        'event_kind': port_event.event_kind,
        'up_count': port_event.up_count,
        'down_count': port_event.down_count,
        'time_create': datetime_to_string(port_event.time_create),
        'time_modify': datetime_to_string(port_event.time_modify)
    }


def port_event_of_orient(orient_entry):
    args = [
        orient_entry.id,
        datetime_of_java_time(orient_entry.time),
        orient_entry.switch_id,
        orient_entry.port_number,
        orient_entry.event
    ]

    kwargs = _get_generic_orient_fields(orient_entry)
    for name in 'up_count', 'down_count':
        try:
            kwargs[name] = getattr(orient_entry, name)
        except AttributeError:
            pass
    return model.PortEvent(*args, **kwargs)


def port_event_of_mysql(mysql_row):
    source = dict(mysql_row)
    (
        event_id, event_time, switch_id, port_number, event,
        unstructured
    ) = _extract_dict_values(
        source, 'id', 'event_time', 'switch_id', 'port_number', 'event',
        'unstructured')
    event_time = datetime_force_timezone(event_time)
    unstructured = json.loads(unstructured)

    kwargs = _get_generic_mysql_fields(mysql_row)
    kwargs.update(_get_optional_keys(
        unstructured,
        up_count='up_events_count', down_count='down_events_count'))

    return model.PortEvent(
        event_id, event_time, switch_id, port_number, event, **kwargs)


def port_event_of_dump(dump_entry):
    source = dump_entry.copy()

    (
        event_id, event_time, switch_id, port_number, event_kind,
        up_count, down_count, time_create, time_modify
    ) = _extract_dict_values(
        source,
        'event_id', 'event_time', 'switch_id', 'port_number', 'event_kind',
        'up_count', 'down_count', 'time_create', 'time_modify',
        allow_extra=False)
    event_time = datetime_of_string(event_time)
    time_create = datetime_of_string(time_create)
    time_modify = datetime_of_string(time_modify)

    return model.PortEvent(
        event_id, event_time, switch_id, port_number, event_kind,
        up_count=up_count, down_count=down_count,
        time_create=time_create, time_modify=time_modify)


# -- type tag --

def type_tag_to_dump(tag):
    return {'type-tag': tag.tag}


def type_tag_of_dump(raw):
    return model.TypeTag(*_extract_dict_values(
        raw, 'type-tag', allow_extra=False))


# -- tools --

def datetime_to_string(value):
    if value is None:
        return value
    if not value.microsecond:
        return value.strftime("%Y-%m-%dT%H:%M:%SZ")
    result = value.strftime("%Y-%m-%dT%H:%M:%S")
    result += '.{:03d}Z'.format(value.microsecond // 1000)
    return result


def datetime_to_java_time(value):
    return int(value.timestamp() * 1000)


def datetime_of_string(source):
    if source is None:
        return source

    time_format_templates = ("%Y-%m-%dT%H:%M:%S.%fZ", "%Y-%m-%dT%H:%M:%SZ")
    for template in time_format_templates:
        try:
            naive = datetime.datetime.strptime(source, template)
        except ValueError:
            continue
        return naive.replace(tzinfo=datetime.timezone.utc)
    raise ValueError(
        'Unable to parse time string {!r} using formats: "{}"'.format(
            source, '", "'.join(time_format_templates)))


def datetime_of_java_time(source):
    return datetime_of_unixtime(source / 1000)


def datetime_of_unixtime(source):
    return datetime.datetime.fromtimestamp(source, datetime.timezone.utc)


def datetime_force_timezone(value, tz=datetime.timezone.utc):
    if value.tzinfo is not None:
        return value
    return value.replace(tzinfo=tz)


def _get_generic_orient_fields(orient_entry):
    return _decode_time_markers(
        _get_optional_attrs(orient_entry, 'time_create', 'time_modify'))


def _get_generic_mysql_fields(mysql_row):
    return _decode_time_markers(
        _get_optional_keys(mysql_row, 'time_create', 'time_modify'))


def _decode_time_markers(payload, fields=None):
    if not fields:
        fields = 'time_create', 'time_modify'
    for name in fields:
        try:
            value = payload[name]
        except KeyError:
            continue
        if isinstance(value, datetime.datetime):
            value = datetime_force_timezone(value)
        else:
            value = datetime_of_string(value)
        payload[name] = value
    return payload


def _get_optional_attrs(source, *attrs, **remap):
    renamed = sorted(remap)
    return _get_optional_fields(
        source, zip(
            itertools.chain(attrs, renamed),
            (operator.attrgetter(x) for x in itertools.chain(
                attrs, ((remap[x] for x in renamed))))))


def _get_optional_keys(source, *keys, **remap):
    renamed = sorted(remap)
    return _get_optional_fields(
        source, zip(
            itertools.chain(keys, renamed),
            (operator.itemgetter(x) for x in itertools.chain(
                keys, ((remap[x] for x in renamed))))))


def _get_optional_fields(source, targets_and_extractors):
    fields = {}
    for name, getter in targets_and_extractors:
        try:
            fields[name] = getter(source)
        except (KeyError, AttributeError):
            continue
    return fields


def _extract_dict_values(record, *keys, allow_extra=True):
    sequence = []
    missing = []
    for name in keys:
        try:
            value = record.pop(name)
        except KeyError:
            value = None
            missing.append(name)
        sequence.append(value)

    if missing:
        raise ValueError(
            'The record have no field(s): "{}"'.format('", "'.join(missing)))
    if not allow_extra and bool(record):
        raise ValueError('The record have extra fields: "{}"'.format(
            '", "'.join(record)))

    return sequence


def _apply_action_if_not_none(action, *sequence):
    results = []
    for entry in sequence:
        if entry is not None:
            entry = action(entry)
        results.append(entry)
    return results
