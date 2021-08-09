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

from . import tools


class PullStatsOutput(tools.StatusOutputBase):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self._flow_events_count = 0
        self._port_events_count = 0

    def record_flow_event(self):
        self._flow_events_count += 1

    def record_port_event(self):
        self._port_events_count += 1

    def _format_message(self):
        chunks = super()._format_message()
        chunks.append('processed ')
        payload_offset = len(chunks)
        for kind, count in (
                ('flow', self._flow_events_count),
                ('port', self._port_events_count)):
            if payload_offset != len(chunks):
                chunks.append(', ')
            chunks.append('{} {} records'.format(count, kind))
        return chunks


class QueryFactory:
    def produce(self, counter, limit):
        raise NotImplementedError


