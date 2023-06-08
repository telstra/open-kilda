# mapping.py

from kilda.tsdb_dump_restore import constants
from kilda.tsdb_dump_restore import stats_client
from kilda.tsdb_dump_restore import utils

import csv
import ast

def encode_stats_entry(entry):
    result = {}
    _timestamp_adapter.write(
        result, utils.datetime_to_unixtime(entry.timestamp))
    _metric_name_adapter.write(result, entry.name)
    _tags_adapter.write(result, dict(entry.tags))
    _value_adapter.write(result, entry.value)

    return result

def get_csv_writer(file):
    return csv.DictWriter(
        file,
        fieldnames=_fieldnames,
        delimiter='|',
        extrasaction='ignore')

def decode_raw_cvs_row(raw):
    row = raw.decode('utf-8')
    row = row.split(_delimiter)
    timestamp = float(row[0])
    metric_name = row[1]
    tags = ast.literal_eval(row[2])
    value = int(row[3])
    return stats_client.StatsEntry(
        utils.unixtime_to_datetime(timestamp), metric_name, value, tags=tags)


class Adapter:
    def __init__(self, field):
        self._field = field

    def write(self, target, value):
        target[self._field] = value

    def read(self, target):
        return target[self._field]


_timestamp_adapter = Adapter(constants.TIMESTAMP_FIELD)
_metric_name_adapter = Adapter(constants.METRIC_NAME_FIELD)
_tags_adapter = Adapter(constants.TAGS_FIELD)
_value_adapter = Adapter(constants.VALUE_FIELD)
_fieldnames=[
            constants.TIMESTAMP_FIELD,
            constants.METRIC_NAME_FIELD,
            constants.TAGS_FIELD,
            constants.VALUE_FIELD,
        ]
_delimiter='|'