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
import itertools
import operator

from . import tools


def count_history_orphaned_record(orient_client, stream_out):
    with OrphanedStatusOutput() as output:
        stream_actions = _lookup_orphaned_actions(orient_client, output)
        stream_actions = (('flow_action', x) for x in stream_actions)
        stream_dumps = _lookup_orphaned_dumps(orient_client, output)
        stream_dumps = (('flow_dump', x) for x in stream_dumps)

        for kind, ref in itertools.chain(stream_actions, stream_dumps):
            stream_out.write('{}: {}\n'.format(kind, ref))


def _lookup_orphaned_actions(client, output):
    stream = _orient_stream(client, _flow_actions_query)
    stream = _group_stream_entries(
        stream, operator.attrgetter('task_id'))

    for entry in stream:
        count = len(entry.batch)
        output.record_processed_actions(count)
        if not _check_flow_event_existence(client, entry.ref):
            output.record_orphaned_action(count)
            yield entry.ref
        output.flush()


def _lookup_orphaned_dumps(client, output):
    stream = _orient_stream(client, _flow_dumps_query)
    stream = _group_stream_entries(
        stream, operator.attrgetter('task_id'))

    for entry in stream:
        count = len(entry.batch)
        output.record_processed_dumps(count)
        if not _check_flow_event_existence(client, entry.ref):
            output.record_orphaned_dump(count)
            yield entry.ref
        output.flush()


def _check_flow_event_existence(client, task_id):
    q = (
        'SELECT count(*) as events FROM flow_event '
        'WHERE task_id="{}"').format(task_id)
    return 0 < _orient_query_scalar(client, q, 'events')


def _group_stream_entries(stream, ref_extractor):
    last_ref = None
    batch = []
    for entry in stream:
        ref = ref_extractor(entry)
        if last_ref == ref:
            batch.append(entry)
            continue

        if batch:
            yield _StreamEntriesGroup(last_ref, batch)
        last_ref = ref
        batch = [entry]
    if batch:
        yield _StreamEntriesGroup(last_ref, batch)


def _orient_stream(client, query_factory, chunk_size=10):
    entry_counter = tools.StreamEntryCounter()
    previous = None
    while True:
        origin = entry_counter.current()
        if previous == origin:
            break
        previous = origin

        q = query_factory(origin)
        for entry in client.query(q, chunk_size):
            entry_counter.update(entry)
            yield entry


def _orient_query_scalar(client, query, field):
    batch = list(client.query(query))
    if 0 == len(batch):
        raise ValueError('There is no results for query: {!r}'.format(query))
    if 1 < len(batch):
        raise ValueError(
            'There is multiple ({} entries) results for query: {!r}'.format(
                len(batch), query))
    entry = batch[0]
    return getattr(entry, field)


def _flow_actions_query(offset):
    chunks = ['SELECT FROM flow_history ORDER BY task_id, @rid']
    if offset:
        chunks.append('SKIP {}'.format(offset))
    return ' '.join(chunks)


def _flow_dumps_query(offset):
    chunks = ['SELECT FROM flow_dump ORDER BY task_id, @rid']
    if offset:
        chunks.append('SKIP {}'.format(offset))
    return ' '.join(chunks)


class OrphanedStatusOutput(tools.StatusOutputBase):
    def __init__(self, **kwargs):
        super().__init__('Lookup orphaned history records:')
        self._processed_actions = 0
        self._processed_dumps = 0
        self._orphaned_actions = 0
        self._orphaned_dumps = 0

    def record_processed_actions(self, count):
        self._processed_actions += count

    def record_processed_dumps(self, count):
        self._processed_dumps += count

    def record_orphaned_action(self, count):
        self._orphaned_actions += count

    def record_orphaned_dump(self, count):
        self._orphaned_dumps += count

    def _format_message(self):
        chunks = super()._format_message()
        payload_offset = len(chunks)
        for total, orphaned, name in (
                (self._processed_actions, self._orphaned_actions, 'actions'),
                (self._processed_dumps, self._orphaned_dumps, 'dumps')):
            if payload_offset < len(chunks):
                chunks.append(', ')
            chunks.append(orphaned)
            chunks.append(' orphaned of ')
            chunks.append(total)
            chunks.append(' ')
            chunks.append(name)
        return chunks


_StreamEntriesGroup = collections.namedtuple(
    '_StreamEntriesGroup', ('ref', 'batch'))
