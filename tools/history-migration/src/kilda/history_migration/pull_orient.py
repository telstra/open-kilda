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

import ndjson

from . import mapper
from . import model
from . import pull
from . import tools


def orient_to_ndjson(orient_client, stream_out, targets, time_range):
    writer = ndjson.writer(stream_out)
    with pull.PullStatsOutput('Pull orient DB') as output:
        if model.PullTarget.HISTORY_FLOW_EVENT in targets:
            _pull_flow_events(orient_client, time_range, writer, output)
        output.flush()
        if model.PullTarget.HISTORY_PORT_EVENT in targets:
            _pull_port_events(orient_client, time_range, writer, output)


def _pull_flow_events(client, time_range, writer, output):
    query_factory = _FlowEventsQueryFactory(time_range)
    stream = _orient_stream(client, query_factory)
    stream = _flow_events_relations_mixin(stream, client)

    writer.writerow(mapper.type_tag_to_dump(model.FLOW_EVENT_TAG))
    for entry in stream:
        output.flush()
        writer.writerow(mapper.flow_event_to_dump(entry))
        output.record_flow_event()


def _pull_port_events(orient_client, time_range, writer, output):
    query_factory = _PortEventsQueryFactory(time_range)
    stream = _orient_stream(orient_client, query_factory)
    stream = (mapper.port_event_of_orient(x) for x in stream)

    writer.writerow(mapper.type_tag_to_dump(model.PORT_EVENT_TAG))
    for port_event in stream:
        output.flush()
        writer.writerow(mapper.port_event_to_dump(port_event))
        output.record_port_event()


def _flow_events_relations_mixin(stream, orient_client):
    for flow_event in stream:
        actions = list(_query_flow_event_actions(
            orient_client, flow_event.task_id))
        dumps = list(_query_flow_event_dumps(
            orient_client, flow_event.task_id))

        yield mapper.flow_event_of_orient(flow_event, actions, dumps)


def _query_flow_event_actions(orient_client, task_id):
    q = 'SELECT FROM flow_history WHERE task_id={!r}'.format(task_id)
    return orient_client.query(q, -1)


def _query_flow_event_dumps(orient_client, task_id):
    q = 'SELECT FROM flow_dump WHERE task_id={!r}'.format(task_id)
    return orient_client.query(q, -1)


def _orient_stream(orient_client, query_factory, limit=1024):
    entry_counter = tools.StreamEntryCounter()
    while True:
        q = query_factory.produce(entry_counter, limit)
        # q = 'SELECT * FROM {} ORDER BY @rid SKIP {}'.format(
        #     orient_class, offset)

        origin = entry_counter.current()
        for entry in orient_client.query(q, limit):
            entry_counter.update(entry)
            yield entry
        if origin == entry_counter.current():
            break


class _OrientDBQueryFactory(pull.QueryFactory):
    def __init__(self, orient_class, timestamp_field, time_range):
        self._orient_class = orient_class
        self._timestamp_field = timestamp_field
        self._time_range = time_range

    def produce(self, counter, limit):
        where_clauses = []
        self._add_time_range_clause(where_clauses)
        chunks = [
            'SELECT FROM {}'.format(self._orient_class),
            self._render_where_expression(where_clauses),
            self._render_order_by_expression()]
        offset = counter.current()
        if offset:
            chunks.append('SKIP {}'.format(offset))
        return ' '.join(x for x in chunks if x)

    def _add_time_range_clause(self, clauses):
        for value, op in zip(self._time_range, ('>', '<=')):
            if value is None:
                continue
            clauses.append('{} {} {}'.format(
                self._timestamp_field, op,
                mapper.datetime_to_java_time(value)))
        return clauses

    def _render_where_expression(self, clauses):
        if not clauses:
            return ''
        return 'WHERE ' + ' AND '.join(clauses)

    def _render_order_by_expression(self):
        raise NotImplementedError


class _FlowEventsQueryFactory(_OrientDBQueryFactory):
    def __init__(self, time_range):
        super().__init__('flow_event', 'timestamp', time_range)

    def _render_order_by_expression(self):
        return 'ORDER BY {}, flow_id'.format(self._timestamp_field)


class _PortEventsQueryFactory(_OrientDBQueryFactory):
    def __init__(self, time_range):
        super().__init__('port_history', 'time', time_range)

    def _render_order_by_expression(self):
        return 'ORDER BY {}, id'.format(self._timestamp_field)
