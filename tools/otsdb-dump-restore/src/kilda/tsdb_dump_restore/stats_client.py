# stats_tsdb.py

import collections
from collections import abc
import datetime
import string

from kilda.tsdb_dump_restore import utils


class _StatsClientBase:
    def __init__(self, http_session, endpoint):
        self._http_session = http_session
        self._url_factory = utils.HttpUrlFactory(endpoint)


class VictoriaMetricsStatsClient(_StatsClientBase):
    def query_range(self, start, end, metric_name, tags=None, is_rate=False):
        if not tags:
            tags = dict()
        response = self._http_session.get(
            self._url_factory.produce(
                'api', 'v1', 'query_range',
                start=self._format_time_query_arg(start),
                end=self._format_time_query_arg(end),
                query=self._build_query(metric_name, tags, is_rate)))
        response.raise_for_status()
        wrapper = self._unpack_response(response.json())
        return self._parse_matrix_response(wrapper, metric_name)

    @classmethod
    def _build_query(cls, metric_name, tags, is_rate):
        query = [
            cls._escape(metric_name),
            '{',
            ','.join('{}="{}"'.format(
                cls._escape(name), value)
                     for (name, value) in tags.items()),
            '}']
        query = ''.join(query)
        if is_rate:
            query = 'rate({})'.format(query)
        return query

    @staticmethod
    def _escape(origin):
        return origin.replace('-', r'\-')

    @staticmethod
    def _unpack_response(response):
        try:
            status = response['status']
        except KeyError as e:
            raise ValueError(
                'Invalid response format - '
                'root object have no key "{}"'.format(e))
        if status != 'success':
            raise ValueError('Victoriametrics query have failed')
        return response['data']

    @classmethod
    def _parse_matrix_response(cls, wrapper, metric_name_fallback):
        result_type = wrapper.get('resultType')
        if result_type != 'matrix':
            raise ValueError(
                'Unexpected API result type "{}"'.format(result_type))

        by_name = collections.defaultdict(list)
        for raw_entry in wrapper['result']:
            metric = raw_entry['metric']
            tags = dict(metric)
            name = tags.pop('__name__', metric_name_fallback)
            values = tuple(
                cls._decode_matrix_value(x)
                for x in raw_entry['values'])

            by_name[name].append(_StatsEntriesBatch(
                name, tags, values, []))

        return _QueryResult(dict(by_name))

    @staticmethod
    def _decode_matrix_value(raw):
        timestamp, value = raw
        timestamp = datetime.datetime.fromtimestamp(
            timestamp, datetime.timezone.utc)

        return _StatsEntryValue(timestamp, _decode_numeric_value(value))

    @staticmethod
    def _format_time_query_arg(value):
        value_utc = value.astimezone(datetime.timezone.utc)
        return value_utc.isoformat()


class OpenTSDBStatsClient(_StatsClientBase):
    def query_range(self, start, end, metric_name, tags=None, is_rate=False):
        if not tags:
            tags = dict()

        agg_func = 'max'
        if is_rate:
            agg_func = 'rate'

        response = self._http_session.get(
            self._url_factory.produce(
                'api', 'query',
                start=self._format_time_query_arg(start),
                end=self._format_time_query_arg(end),
                m=self._build_query(metric_name, tags, agg_func)))
        response.raise_for_status()
        return self._parse_query_response(response.json())

    @classmethod
    def _build_query(cls, metric_name, tags, agg_func):
        query = [metric_name]
        if tags:
            query.extend((
                '{',
                ','.join('{}={}'.format(name, value)
                         for (name, value) in tags.items()),
                '}'))
        query = ''.join(query)
        handlers = [agg_func, query]
        return ':'.join(handlers)

    @staticmethod
    def _format_time_query_arg(value):
        return int(utils.datetime_to_unixtime(value))

    @classmethod
    def _parse_query_response(cls, payload):
        by_name = collections.defaultdict(list)
        for entry in payload:
            name = entry['metric']
            tags = entry['tags']
            agg_tags = entry.get('aggregateTags', [])
            values = cls._parse_dps(entry['dps'])
            by_name[name].append(
                _StatsEntriesBatch(name, tags, values, agg_tags))
        return _QueryResult(dict(by_name))

    @classmethod
    def _parse_dps(cls, payload):
        if isinstance(payload, abc.Mapping):
            return sorted(
                cls._parse_dps_stream(payload.items()),
                key=lambda x: x.timestamp)
        return cls._parse_dps_stream(payload)

    @staticmethod
    def _parse_dps_stream(stream):
        results = []
        for timestamp, value in stream:
            timestamp = int(timestamp)
            timestamp = datetime.datetime.fromtimestamp(
                timestamp, datetime.timezone.utc)
            value = _decode_numeric_value(value)
            results.append(_StatsEntryValue(timestamp, value))
        return results


class OpenTSDBMetricsList(abc.Iterator):
    _allowed_chars = string.ascii_letters + string.digits + '-_./'

    def __init__(self, http_session, endpoint, prefix='', batch_size=25):
        self._http_session = http_session
        self._url_factory = utils.HttpUrlFactory(endpoint)
        self._batch_size = batch_size

        self._known_metrics = set()
        self._ready = iter([])
        self._queue = [iter([prefix])]

        self._api_requests_count = 0

    def __next__(self):
        try:
            return next(self._ready)
        except StopIteration:
            pass

        while self._queue:
            for prefix in self._queue[0]:
                suggests = self._query_suggest(prefix, self._batch_size)
                if self._batch_size <= len(suggests):
                    self._queue.append(self._new_queue_entry(prefix))
                new_entries = set(suggests) - self._known_metrics
                if new_entries:
                    break
            else:
                self._queue.pop(0)
                continue

            self._known_metrics.update(new_entries)
            self._ready = iter(new_entries)
            break

        return next(self._ready)

    def _query_suggest(self, prefix, limit):
        args = {'type': 'metrics', 'q': prefix, 'max': limit}
        url = self._url_factory.produce('api', 'suggest', **args)
        self._api_requests_count += 1
        response = self._http_session.get(url)
        response.raise_for_status()
        return set(response.json())

    @classmethod
    def _new_queue_entry(cls, prefix):
        return ((prefix + x) for x in cls._allowed_chars)


def batch_to_entries(batch):
    return [
        StatsEntry(
            x.timestamp, batch.name, x.value, batch.tags)
        for x in batch.values]


class StatsEntry:
    __slots__ = ('timestamp', 'name', 'tags', 'value')

    def __init__(self, timestamp, name, value, tags=None):
        self.timestamp = timestamp
        self.name = name
        self.value = value
        if not tags:
            tags = {}
        self.tags = tags

    def __str__(self):
        return '{}({}, {}, {}, tags={})'.format(
            StatsEntry.__name__, self.timestamp, self.name,
            self.value, self.tags)


class _QueryResult:
    def __init__(self, by_name):
        self._by_name = by_name

    def lookup(self, name):
        try:
            entry = self._by_name[name]
        except KeyError:
            raise ValueError(
                'There is no metric {!r} in stats results'.format(name))
        return entry

    def lookup_values(self, name):
        results = []
        for entry in self.lookup(name):
            results.extend(entry.values)
        return results

    def lookup_entries(self, name):
        results = []
        for batch in self.lookup(name):
            results.extend(batch_to_entries(batch))
        return results

    def get_metrics_list(self):
        return list(self._by_name)


class _StatsEntriesBatch:
    __slots__ = ('name', 'tags', 'aggregate_tags', 'values')

    def __init__(self, name, tags, values, aggregate_tags):
        self.name = name
        self.tags = tags
        self.values = values
        self.aggregate_tags = aggregate_tags


class _StatsEntryValue:
    __slots__ = ('timestamp', 'value')

    def __init__(self, timestamp, value):
        self.timestamp = timestamp
        self.value = value


def _decode_numeric_value(origin):
    value_int = int(origin)
    value_float = float(origin)

    if value_int == value_float:
        return value_int
    return value_float
