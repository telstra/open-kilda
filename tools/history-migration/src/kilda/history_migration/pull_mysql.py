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

import ndjson

from . import mapper
from . import model
from . import pull
from . import tools


def mysql_to_ndjson(mysql_client, stream_out, targets, time_range):
    writer = ndjson.writer(stream_out)
    with pull.PullStatsOutput('Pull orient DB') as output:
        if model.PullTarget.HISTORY_FLOW_EVENT in targets:
            _pull_flow_events(mysql_client, time_range, writer, output)
        output.flush()
        if model.PullTarget.HISTORY_PORT_EVENT in targets:
            _pull_port_events(mysql_client, time_range, writer, output)


def _pull_flow_events(client, time_range, writer, output):
    stream = _mysql_stream(client, _FlowEventsQueryFactory(time_range))
    stream = _flow_events_relations_mixin(stream, client)

    writer.writerow(mapper.type_tag_to_dump(model.FLOW_EVENT_TAG))
    for entry in stream:
        output.flush()
        writer.writerow(mapper.flow_event_to_dump(entry))
        output.record_flow_event()


def _pull_port_events(client, time_range, writer, output):
    stream = _mysql_stream(client, _PortEventsQueryFactory(time_range))
    stream = (mapper.port_event_of_mysql(x) for x in stream)

    writer.writerow(mapper.type_tag_to_dump(model.PORT_EVENT_TAG))
    for entry in stream:
        output.flush()
        writer.writerow(mapper.port_event_to_dump(entry))
        output.record_port_event()


def _flow_events_relations_mixin(stream, client):
    for entry in stream:
        event_id = _extract_flow_event_id(entry)
        actions = _query_flow_event_actions(client, event_id)
        dumps = _query_flow_event_dumps(client, event_id)
        yield mapper.flow_event_of_mysql(entry, actions, dumps)


def _extract_flow_event_id(mysql_row):
    try:
        value = mysql_row['id']
    except KeyError:
        raise ValueError(
            'Unable to extract flow event mysql ID of {!r}'.format(mysql_row))
    return value


def _query_flow_event_actions(client, event_id):
    cursor = client.cursor(dictionary=True)
    cursor.execute(
        'SELECT * FROM flow_event_action WHERE flow_event_id=%s '
        'ORDER BY event_time, id', params=(event_id, ))
    return cursor.fetchall()


def _query_flow_event_dumps(client, event_id):
    cursor = client.cursor(dictionary=True)
    cursor.execute(
        'SELECT * FROM flow_event_dump WHERE flow_event_id=%s '
        'ORDER BY id', params=(event_id,))
    return cursor.fetchall()


def _mysql_stream(client, query_factory, limit=1024):
    entry_counter = tools.StreamEntryCounter()
    while True:
        q, params = query_factory.produce(entry_counter, limit)

        origin = entry_counter.current()
        cursor = client.cursor(dictionary=True)
        cursor.execute(q, params=params)
        for entry in cursor.fetchall():
            entry_counter.update(entry)
            yield entry

        if origin == entry_counter.current():
            break


class _MysqlQueryFactory(pull.QueryFactory):
    def __init__(self, time_range):
        self._time_range = time_range

    def produce(self, counter, limit):
        query_params = []
        chunks = [
            'SELECT * FROM',
            self._render_from_expression(),
            self._render_where_expression(
                query_params, self._make_where_clauses()),
            self._render_order_by_expression(),
            self._render_limit_expression(counter, limit)]
        return ' '.join(x for x in chunks if x), query_params

    def _make_where_clauses(self):
        return []

    def _render_from_expression(self):
        raise NotImplementedError

    def _render_where_expression(self, params, clauses=()):
        if not clauses:
            return ''
        expressions = []
        for entry in clauses:
            expressions.append(entry.chunk)
            params.append(entry.parameters)
        return 'WHERE ' + ' AND '.join(expressions)

    def _render_order_by_expression(self):
        raise NotImplementedError

    def _render_limit_expression(self, counter, limit):
        return 'LIMIT {},{}'.format(counter.current(), limit)

    def _make_time_range_filter(self, time_range, field):
        clauses = []
        for value, op in zip(time_range, ('>', '<=')):
            if value is None:
                continue
            clauses.append(_QueryClauseChunk(
                '{} {} %s'.format(field, op), value))
        return clauses


class _FlowEventsQueryFactory(_MysqlQueryFactory):
    def _make_where_clauses(self):
        clauses = super()._make_where_clauses()
        clauses.extend(self._make_time_range_filter(
            self._time_range, 'event_time'))
        return clauses

    def _render_from_expression(self):
        return 'flow_event'

    def _render_order_by_expression(self):
        field = 'id'
        if any(self._time_range):
            field = 'event_time, id'
        return 'ORDER BY {}'.format(field)


class _PortEventsQueryFactory(_MysqlQueryFactory):
    def _make_where_clauses(self):
        clauses = super()._make_where_clauses()
        clauses.extend(self._make_time_range_filter(
            self._time_range, 'event_time'))
        return clauses

    def _render_from_expression(self):
        return 'port_event'

    def _render_order_by_expression(self):
        return 'ORDER BY event_time, id'


_QueryClauseChunk = collections.namedtuple(
    '_QueryClauseChunk', ('chunk', 'parameters'))
