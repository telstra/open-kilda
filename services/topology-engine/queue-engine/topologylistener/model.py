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


class NetworkEndpoint(
        collections.namedtuple('NetworkEndpoint', ('dpid', 'port'))):

    @classmethod
    def new_from_isl_data_path(cls, path_node):
        return cls(path_node['switch_id'], path_node['port_no'])


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
