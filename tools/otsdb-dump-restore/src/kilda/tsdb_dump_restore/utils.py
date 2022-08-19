# utils.py

from collections import abc
import datetime
import json
from urllib import parse

UNIXTIME_ORIGIN = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)


def time_now():
    now = datetime.datetime.now()
    return now.astimezone(datetime.timezone.utc)


def datetime_to_unixtime(value):
    return (value.astimezone(datetime.timezone.utc) -
            UNIXTIME_ORIGIN).total_seconds()


def datetime_align(origin, align):
    origin_delta = origin - UNIXTIME_ORIGIN
    r = origin_delta % align
    return origin - r


def unixtime_to_millis(value):
    return int(value * 1000)


def unixtime_to_datetime(value):
    return (datetime.datetime.utcfromtimestamp(value)
            .replace(tzinfo=datetime.timezone.utc))


class HttpUrlFactory:
    def __init__(self, base):
        endpoint = parse.urlparse(base)
        update = {}
        if endpoint.path:
            update['path'] = self._force_path_encoding(endpoint.path)
        if update:
            endpoint = endpoint._replace(**update)
        self._endpoint = endpoint

    def produce(self, *path, **query_args):
        update = {}
        if path:
            path = '/'.join(
                parse.quote(x) for x in path)
            update['path'] = '/'.join((self._endpoint.path, path))

        if query_args:
            qs = dict(parse.parse_qs(self._endpoint.query))
            qs.update(query_args)
            query = []
            for name, value in qs.items():
                if (isinstance(value, str)
                        or not isinstance(value, abc.Sequence)):
                    query.append((name, value))
                else:
                    for entry in value:
                        query.append((name, entry))
            update['query'] = parse.urlencode(query, doseq=True)

        return self._endpoint._replace(**update).geturl()

    @staticmethod
    def _force_path_encoding(path):
        decoded = parse.unquote(path)
        return parse.quote(decoded)


class RestStatistics:
    def __init__(self):
        self.requests_count = 0

    def record_response(self, response):
        self.requests_count += 1


class ResponseStatisticsHook:
    def __init__(self, statistics):
        self._statistics = statistics

    def __call__(self, response, **kwargs):
        self._statistics.record_response(response)


class OperationMetadata:
    def __init__(self, path, path_upd):
        self._path = path
        self._path_upd = path_upd

    def read(self):
        try:
            raw = self._read()
        except FileNotFoundError as e:
            raise ValueError('Metadata not available', e)
        return self._decode(raw)

    def write(self, entry):
        with self._path_upd.open('wt') as stream:
            json.dump(self._encode(entry), stream)
        self._path_upd.replace(str(self._path))

    def remove(self):
        try:
            self._path.unlink()
        except FileNotFoundError:
            pass

    def _read(self):
        with self._path.open('rt') as stream:
            return json.load(stream)

    def _encode(self, entry):
        raise NotImplementedError

    def _decode(self, data):
        raise NotImplementedError
