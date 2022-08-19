# restore.py

import os
import pathlib
import re
import json

import click
import requests

from kilda.tsdb_dump_restore import mapping
from kilda.tsdb_dump_restore import report
from kilda.tsdb_dump_restore import utils


@click.command()
@click.option(
    '--dump-dir', type=click.types.Path(file_okay=False), default='.',
    help='Location where dump files are stored')
@click.option(
    '--put-request-size-limit', type=int, default=4096,
    help='Limit for "put" request payload size (bytes)')
@click.argument('opentsdb_endpoint')
def main(opentsdb_endpoint, **options):
    dump_dir = pathlib.Path(options['dump_dir'])
    batch_size_limit = options['put_request_size_limit']

    http_session = requests.Session()

    rest_statistics = utils.RestStatistics()

    http_session.hooks['response'].append(
        utils.ResponseStatisticsHook(rest_statistics))

    stream = stream_source(dump_dir)
    stream = records_stream_map(stream)
    stream = decode_stream_map(stream)
    stream = decode_otsdb_stream_map(stream)
    stream = collect_batches_stream_map(
        stream, batch_size_limit,
        lambda: _Batch(prefix=b'[', suffix=b']', separator=b','))
    stream = push_batch_to_otsdb_stream_map(
        stream, http_session, opentsdb_endpoint)

    metric_report = None
    try:
        for batch, descriptors in stream:
            for d in descriptors:
                metadata = d.stream_entry.metadata
                metadata.write(d.offset_end)

                if metric_report is None:
                    metric_report = RestoreProgressReport(rest_statistics, d)
                if metric_report.metric == d.name:
                    metric_report.update(d)
                else:
                    metric_report.close()
                    metric_report = RestoreProgressReport(rest_statistics, d)
                metric_report.flush()
    finally:
        if metric_report is not None:
            metric_report.close()


def stream_source(path):
    patters = (
        re.compile(r'^(?P<name>.*)\.ndjson$', re.IGNORECASE),)

    for entry in path.iterdir():
        for p in patters:
            m = p.match(entry.name)
            if m is None:
                continue
            name = m.group('name')
            yield _StreamEntry(name, entry)


def records_stream_map(stream, chunk_size=4096, separator=b'\n'):
    for stream_entry in stream:
        try:
            offset = stream_entry.metadata.read()
        except ValueError:
            offset = 0

        with stream_entry.path.open('rb') as fd_stream:
            fd_stream.seek(0, os.SEEK_END)
            total_size = fd_stream.tell()
            fd_stream.seek(offset, os.SEEK_SET)

            chunk = b''
            while True:
                data = fd_stream.read(chunk_size)
                if not data:
                    break

                chunk += data
                while True:
                    entry, match, tail = chunk.partition(separator)
                    if not match:
                        break

                    size = len(entry) + len(match)
                    yield stream_entry, entry, offset, size, total_size
                    chunk = tail
                    offset += size

            if chunk:
                size = len(chunk) + len(separator)
                yield stream_entry, chunk, offset, size, total_size


def decode_stream_map(stream):
    for stream_entry, record, offset, size, total_size in stream:
        if not record:
            continue
        data = json.loads(record)
        entry = mapping.decode_stats_entry(data)
        yield stream_entry, entry, offset, size, total_size


def decode_otsdb_stream_map(stream):
    for stream_entry, record, offset, size, total_size in stream:
        entry = _stats_to_otsdb_json_map(record)
        entry = entry.encode('utf-8')
        yield stream_entry, entry, offset, size, total_size


def _stats_to_otsdb_json_map(stats_entry):
    unixtime = utils.datetime_to_unixtime(stats_entry.timestamp)
    timestamp = utils.unixtime_to_millis(unixtime)
    return json.dumps({
        "metric": stats_entry.name,
        "timestamp": timestamp,
        "value": stats_entry.value,
        "tags": stats_entry.tags})


def collect_batches_stream_map(stream, size_limit, batch_factory):
    stream_descriptors = []
    batch = batch_factory()
    for stream_entry, record, offset, size, total_size in stream:
        # print('A: {} {} {}'.format(name, offset, size))
        batch.add(record)

        if not stream_descriptors:
            stream_descriptors = [_StreamChunkDescriptor(stream_entry)]
        if stream_descriptors[-1].name != stream_entry.name:
            stream_descriptors.append(_StreamChunkDescriptor(stream_entry))
        stream_descriptors[-1].update(offset, size, total_size)

        if batch.size() < size_limit:
            continue

        yield batch, stream_descriptors

        batch = batch_factory()
        stream_descriptors = []

    if not batch.is_empty():
        yield batch, stream_descriptors


def push_batch_to_otsdb_stream_map(stream, http_session, endpoint):
    url = utils.HttpUrlFactory(endpoint).produce('api', 'put')
    for batch, descriptors in stream:
        payload = batch.assemble()
        response = http_session.post(
            url, payload, headers={
                'content-type': 'application/json'
            })

        response.raise_for_status()
        yield batch, descriptors


class _StreamEntry:
    __slots__ = ('name', 'path', 'metadata')

    def __init__(self, name, path):
        self.name = name
        self.path = path
        self.metadata = _RestoreMetadata(path)


class _StreamChunkDescriptor:
    def __init__(self, stream_entry):
        self.stream_entry = stream_entry
        self.name = stream_entry.name

        self.offset_start = self.offset_end = -1
        self.stream_size = 0
        self.entries_count = 0

    def update(self, offset, size, total_size):
        if self.offset_start < 0:
            # print('C0: {} {}'.format(self.name, offset))
            self.offset_start = offset
            self.offset_end = offset + size
        else:
            # print('C1: {} {}'.format(self.name, offset))
            self.offset_start = min(self.offset_start, offset)
            self.offset_end = max(self.offset_end, offset + size)
        self.stream_size = total_size
        self.entries_count += 1
        # print('CE: {} {}'.format(self.name, self.offset_start))


class _Batch:
    def __init__(self, separator='', prefix='', suffix=''):
        self._separator = separator
        self._prefix = prefix
        self._suffix = suffix

        self._size = len(prefix) + len(suffix)
        self._entries = []

    def add(self, entry):
        size_diff = len(entry)
        if 0 < len(self._entries):
            size_diff += len(self._separator)

        self._entries.append(entry)
        self._size += size_diff

    def assemble(self):
        return (self._prefix +
                self._separator.join(self._entries) +
                self._suffix)

    def size(self):
        return self._size

    def is_empty(self):
        return not self._entries


class _RestoreMetadata(utils.OperationMetadata):
    def __init__(self, path):
        super().__init__(
            path.parent / (path.name + '.rmeta.json'),
            path.parent / (path.name + '.rmeta.json.upd'))

    def _encode(self, entry):
        return {'offset': entry}

    def _decode(self, data):
        return data['offset']


class RestoreProgressReport(report.ProgressReportBase):
    def __init__(self, rest_statistics, stream_descriptor, **kwargs):
        super().__init__(**kwargs)
        self.rest_statistics = rest_statistics
        self.metric = stream_descriptor.name
        self._entries_count = stream_descriptor.entries_count
        self._stream_descriptor = stream_descriptor

    def update(self, stream_descriptor):
        self._stream_descriptor = stream_descriptor
        self._entries_count += stream_descriptor.entries_count

    def _format_message(self):
        descriptor = self._stream_descriptor
        one_percent = descriptor.stream_size / 100
        if 0 < one_percent:
            percent = descriptor.offset_end / one_percent
        else:
            percent = None
        if (descriptor.stream_size
                and descriptor.stream_size <= descriptor.offset_end):
            percent = 100

        if percent is None:
            percent = '???'
        else:
            percent = '{:.2f}%'.format(percent)

        message = (
            'Restoring "{}" {} entries processed '
            '({} completed, {} http requests)'
        ).format(
            self.metric, self._entries_count, percent,
            self.rest_statistics.requests_count)
        return [message]
