# mapping.py

from kilda.tsdb_dump_restore import stats_client
from kilda.tsdb_dump_restore import utils


def encode_stats_entry(entry):
    result = {}
    _timestamp_adapter.write(
        result, utils.datetime_to_unixtime(entry.timestamp))
    _metric_name_adapter.write(result, entry.name)
    _tags_adapter.write(result, dict(entry.tags))
    _value_adapter.write(result, entry.value)

    return result


def decode_stats_entry(raw):
    timestamp = _timestamp_adapter.read(raw)
    name = _metric_name_adapter.read(raw)
    tags = _tags_adapter.read(raw)
    value = _value_adapter.read(raw)

    return stats_client.StatsEntry(
        utils.unixtime_to_datetime(timestamp), name, value, tags=tags)


class Adapter:
    def __init__(self, field):
        self._field = field

    def write(self, target, value):
        target[self._field] = value

    def read(self, target):
        return target[self._field]


_timestamp_adapter = Adapter('timestamp')
_metric_name_adapter = Adapter('metric')
_tags_adapter = Adapter('tags')
_value_adapter = Adapter('value')
