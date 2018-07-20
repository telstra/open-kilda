# -*- coding:utf-8 -*-
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

import collections
import datetime
import json
import logging
import sys
import uuid
import weakref

import pytz

from topologylistener import exc

logger = logging.getLogger(__name__)


def convert_integer(raw, limit=sys.maxint):
    if not isinstance(raw, (int, long)):
        try:
            value = int(raw, 0)
        except ValueError:
            raise exc.UnacceptableDataError(raw, 'not numeric value: {}'.format(raw))
    else:
        value = raw

    if limit is not None and value > limit:
        raise exc.UnacceptableDataError(
            raw, 'integer value too big {}'.format(raw))
    return value


def dash_to_underscore(source):
    result = {}
    for key in source:
        result[key.replace('-', '_')] = source[key]
    return result


def grab_fields(data, fields_mapping):
    return {
        y: data[x]
        for x, y in fields_mapping.items() if x in data}


LifeCycleFields = collections.namedtuple('LifeCycleFields', ('ctime', 'mtime'))


class TimeProperty(object):
    FORMAT = '%Y-%m-%dT%H:%M:%S.%fZ'
    UNIX_EPOCH = datetime.datetime(1970, 1, 1, 0, 0, 0, 0, pytz.utc)

    @classmethod
    def new_from_java_timestamp(cls, value):
        value = int(value)
        value /= 1000.0
        return cls(datetime.datetime.utcfromtimestamp(value))

    @classmethod
    def new_from_db(cls, value):
        value = datetime.datetime.strptime(value, cls.FORMAT)
        return cls(value)

    @classmethod
    def now(cls, milliseconds_precission=False):
        value = datetime.datetime.utcnow()
        if milliseconds_precission:
            microseconds = value.microsecond
            microseconds -= microseconds % 1000
            value = value.replace(microsecond=microseconds)
        return cls(value)

    def __init__(self, value):
        if value.tzinfo is None:
            value = value.replace(tzinfo=pytz.utc)
        self.value = value

    def __str__(self):
        return self.value.strftime(self.FORMAT)

    def as_java_timestamp(self):
        from_epoch = self.value - self.UNIX_EPOCH
        seconds = int(from_epoch.total_seconds())
        return seconds * 1000 + from_epoch.microseconds // 1000


class JsonSerializable(object):
    pass


class Default(object):
    def __init__(self, value, produce=False, override_none=True):
        self._resolve_cache = weakref.WeakKeyDictionary()
        self.value = value
        self.produce = produce
        self.override_none = override_none

    def __get__(self, instance, owner):
        if instance is None:
            return self

        value = self.value
        if self.produce:
            value = value()

        setattr(instance, self._resolve_name(owner), value)
        return value

    def is_filled(self, instance):
        name = self._resolve_name(type(instance))
        data = vars(instance)
        return name in data

    def _resolve_name(self, owner):
        try:
            return self._resolve_cache[owner]
        except KeyError:
            pass

        for name in dir(owner):
            try:
                attr = getattr(owner, name)
            except AttributeError:
                continue
            if attr is not self:
                continue
            break
        else:
            raise RuntimeError(
                '{!r} Unable to resolve bounded name (UNREACHABLE)'.format(
                    self))

        self._resolve_cache[owner] = name
        return name


class Abstract(object):
    pack_exclude = frozenset()

    def __init__(self, **fields):
        cls = type(self)
        extra = set()
        for name in fields:
            extra.add(name)
            try:
                attr = getattr(cls, name)
            except AttributeError:
                continue
            if not isinstance(attr, Default):
                continue

            extra.remove(name)

            if attr.override_none and fields[name] is None:
                continue
            setattr(self, name, fields[name])

        if extra:
            raise TypeError('{!r} got unknown arguments: "{}"'.format(
                self, '", "'.join(sorted(extra))))

        self._verify_fields()

    def __str__(self):
        return '<{}:{}>'.format(
            type(self).__name__,
            json.dumps(self.pack(), sort_keys=True, cls=JSONEncoder))

    def __eq__(self, other):
        if not isinstance(other, Abstract):
            raise NotImplementedError
        return self._sort_key() == other._sort_key()

    def __ne__(self, other):
        return not self.__eq__(other)

    def pack(self):
        fields = vars(self).copy()

        cls = type(self)
        for name in dir(cls):
            if name.startswith('_'):
                continue
            if name in fields:
                continue

            attr = getattr(cls, name)
            if not isinstance(attr, Default):
                continue
            fields[name] = getattr(self, name)

        for name in tuple(fields):
            if not name.startswith('_') and name not in self.pack_exclude:
                continue
            del fields[name]

        return fields

    def _verify_fields(self):
        pass

    def _sort_key(self):
        raise NotImplementedError

    @classmethod
    def _extract_fields(cls, data):
        data = data.copy()
        fields = {}
        for name in dir(cls):
            if name.startswith('_'):
                continue
            attr = getattr(cls, name)
            if not isinstance(attr, Default):
                continue

            try:
                fields[name] = data.pop(name)
            except KeyError:
                pass

        return fields, data

    @classmethod
    def decode_java_fields(cls, data):
        return {}

    @classmethod
    def decode_db_fields(cls, data):
        return {}


class TimestampMixin(Abstract):
    time_create = Default(None)  # type: TimeProperty
    time_modify = Default(None)  # type: TimeProperty

    def __init__(self, **fields):
        timestamp = TimeProperty.now()
        for name in ('time_create', 'time_modify'):
            fields.setdefault(name, timestamp)
        super(TimestampMixin, self).__init__(**fields)

    @classmethod
    def decode_java_fields(cls, data):
        decoded = super(TimestampMixin, cls).decode_java_fields(data)
        for name in ('time_create', 'time_modify'):
            try:
                if data[name] is None:
                    del data[name]
                    continue

                decoded[name] = TimeProperty.new_from_java_timestamp(data[name])
            except KeyError:
                pass

        try:
            decoded.setdefault('time_modify', decoded['time_create'])
        except KeyError:
            pass

        return decoded

    @classmethod
    def decode_db_fields(cls, data):
        decoded = super(TimestampMixin, cls).decode_db_fields(data)
        for name in ('time_create', 'time_modify'):
            try:
                decoded[name] = TimeProperty.new_from_db(data[name])
            except KeyError:
                pass
        return decoded

    def pack(self):
        fields = super(TimestampMixin, self).pack()
        for name in 'time_create', 'time_modify':
            try:
                fields[name] = fields[name].as_java_timestamp()
            except KeyError:
                pass
        return fields


class AbstractNetworkEndpoint(Abstract):
    def __init__(self, dpid, port, **fields):
        super(AbstractNetworkEndpoint, self).__init__(**fields)

        if isinstance(dpid, basestring):
            dpid = dpid.lower()
        if port is not None:
            port = convert_integer(port)

        self.dpid = dpid
        self.port = port

    def __str__(self):
        return '{}-{}'.format(self.dpid, self.port)

    def _sort_key(self):
        return self.dpid, self.port


class NetworkEndpoint(AbstractNetworkEndpoint):
    @classmethod
    def new_from_java(cls, data):
        return cls(data['switch-id'], data['port-id'])

    def pack(self):
        fields = super(NetworkEndpoint, self).pack()
        for src, dst in [
                ('dpid', 'switch-id'),
                ('port', 'port-id')]:
            fields[dst] = fields.pop(src)
        return fields


class IslPathNode(AbstractNetworkEndpoint):
    @classmethod
    def new_from_java(cls, data):
        return cls(data['switch_id'], data['port_no'])

    def pack(self):
        fields = super(IslPathNode, self).pack()
        for src, dst in [
                ('dpid', 'switch_id'),
                ('port', 'port_no')]:
            fields[dst] = fields.pop(src)
        return fields


class AbstractLink(Abstract):
    source = Default(None)
    dest = Default(None)

    def __init__(self, **fields):
        super(AbstractLink, self).__init__(**fields)

    def _verify_fields(self):
        super(AbstractLink, self)._verify_fields()
        if not self.source or not self.dest:
            raise exc.UnacceptableDataError(
                self, (
                    'can\'t instantiate {} without defining both '
                    'source=={!r} and dest=={!r} fields').format(
                    type(self).__name__, self.source, self.dest))

    def _sort_key(self):
        return self.source, self.dest


class LinkProps(TimestampMixin, AbstractLink):
    isl_protected_fields = frozenset((
        'time_create', 'time_modify',
        'latency', 'speed', 'available_bandwidth', 'actual', 'status'))
    props_converters = {
        'cost': convert_integer}

    props = Default(dict, produce=True)
    filtered = Default(set, produce=True)

    @classmethod
    def new_from_java(cls, data):
        data = data.copy()
        source = NetworkEndpoint.new_from_java(data.pop('source'))
        dest = NetworkEndpoint.new_from_java(data.pop('dest'))
        props = data.pop('props', dict()).copy()
        data.update(cls.decode_java_fields(data))
        return cls(source, dest, props=props, **data)

    @classmethod
    def new_from_db(cls, data):
        data = data.copy()

        endpoints = []
        for prefix in ('src_', 'dst_'):
            dpid = data.pop(prefix + 'switch')
            port = data.pop(prefix + 'port')
            endpoints.append(NetworkEndpoint(dpid, port))
        source, dest = endpoints

        data, props = cls._extract_fields(data)
        data.update(cls.decode_db_fields(data))

        return cls(source, dest, props=props, **data)

    @classmethod
    def new_from_isl(cls, isl):
        return cls(isl.source, isl.dest)

    def __init__(self, source, dest, **fields):
        super(LinkProps, self).__init__(source=source, dest=dest, **fields)

    def props_db_view(self):
        props = self._decode_props(self.props)
        return props

    def extract_protected_props(self):
        filtered = {}
        for field in self.isl_protected_fields:
            try:
                filtered[field] = self.props.pop(field)
            except KeyError:
                pass
        return filtered

    @classmethod
    def _decode_props(cls, props):
        for field, converter in cls.props_converters.items():
            try:
                value = props[field]
            except KeyError:
                continue
            props[field] = converter(value)
        return props

    def pack(self):
        fields = super(LinkProps, self).pack()
        fields.pop('filtered')
        return fields


class InterSwitchLink(TimestampMixin, AbstractLink):
    state = Default(None)

    @classmethod
    def new_from_java(cls, data):
        try:
            path = data['path']
            endpoints = [
                IslPathNode.new_from_java(x)
                for x in path]
        except KeyError as e:
            raise ValueError((
                 'Invalid record format "path": is not contain key '
                 '{}').format(e))

        if 2 == len(endpoints):
            pass
        elif 1 == len(endpoints):
            endpoints.append(None)
        else:
            raise ValueError(
                'Invalid record format "path": expect list with 1 or 2 nodes')

        source, dest = endpoints
        return cls(source, dest, data['state'])

    @classmethod
    def new_from_db(cls, link):
        source = IslPathNode(link['src_switch'], link['src_port'])
        dest = IslPathNode(link['dst_switch'], link['dst_port'])
        return cls(source, dest, link['status'])

    @classmethod
    def new_from_link_props(cls, link_props):
        endpoints = [
            IslPathNode(x.dpid, x.port)
            for x in link_props.source, link_props.dest]
        return cls(*endpoints)

    def __init__(self, source, dest, state=None, **fields):
        super(InterSwitchLink, self).__init__(
            source=source, dest=dest, state=state, **fields)

    def ensure_path_complete(self):
        ends_count = len(filter(None, (self.source, self.dest)))
        if ends_count != 2:
            raise ValueError(
                'ISL path not define %s/2 ends'.format(ends_count))

    def reversed(self):
        cls = type(self)
        return cls(self.dest, self.source, self.state)

    def __str__(self):
        return '{} <===> {}'.format(self.source, self.dest)


class JSONEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, TimeProperty):
            value = str(o)
        elif isinstance(o, uuid.UUID):
            value = str(o)
        elif isinstance(o, JsonSerializable):
            value = vars(o)
        elif isinstance(o, Abstract):
            value = o.pack()
        else:
            value = super(JSONEncoder, self).default(o)
        return value
