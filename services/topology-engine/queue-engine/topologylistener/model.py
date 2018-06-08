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

import datetime
import collections
import json

import pytz


def convert_integer(raw):
    if isinstance(raw, (int, long)):
        return raw
    return int(raw, 0)


class NetworkEndpoint(
        collections.namedtuple('NetworkEndpoint', ('dpid', 'port'))):

    @classmethod
    def new_from_isl_data_path(cls, path_node):
        return cls(path_node['switch_id'], path_node['port_no'])

    def __str__(self):
        return '{}-{}'.format(*self)


class InterSwitchLink(
        collections.namedtuple(
            'InterSwitchLink', ('source', 'dest', 'state'))):

    @classmethod
    def new_from_isl_data(cls, isl_data):
        try:
            path = isl_data['path']
            endpoints = [
                NetworkEndpoint.new_from_isl_data_path(x)
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
        return cls(source, dest, isl_data['state'])

    @classmethod
    def new_from_db(cls, link):
        source = NetworkEndpoint(link['src_switch'], link['src_port'])
        dest = NetworkEndpoint(link['dst_switch'], link['dst_port'])
        return cls(source, dest, link['status'])

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


class JSONEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, TimeProperty):
            result = str(o)
        elif isinstance(o, JsonSerializable):
            result = vars(o)
        else:
            result = super(JSONEncoder, self).default(o)
        return result
