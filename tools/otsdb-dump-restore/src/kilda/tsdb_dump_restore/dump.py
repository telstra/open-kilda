# dump.py

import datetime
import pathlib

import click
import ndjson
import requests

from kilda.tsdb_dump_restore import mapping
from kilda.tsdb_dump_restore import stats_client
from kilda.tsdb_dump_restore import report
from kilda.tsdb_dump_restore import utils

ZERO_TIMEDELTA = datetime.timedelta()


@click.command()
@click.option(
    '--time-stop', type=click.types.DateTime(), metavar='TIME_STOP',
    default=str(datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S")),
    help='Timestamp where to stop dumping  [default: NOW]')
@click.option(
    '--dump-dir', type=click.types.Path(file_okay=False), default='.',
    help='Location where dump files will be stored')
@click.option(
    '--query-frame-size', type=int, default=180, show_default=True,
    help='OpenTSDB query time frame size (seconds)')
@click.option(
    '--metrics-prefix', default='kilda.', show_default=True,
    help='Only metrics that match this prefix will be dumped')
@click.option(
    '--remove-metadata', is_flag=True)
@click.argument('opentsdb_endpoint')
@click.argument(
    'time_start', type=click.types.DateTime(), metavar='TIME_START')
def main(opentsdb_endpoint, time_start, **options):
    """
    This tool dumps the data from an OpenTSDB

    OPENTSDB_ENDPOINT openTSDB endpoint

    TIME_START time since the data is dumped

    Example:
    kilda-otsdb-dump http://example.com:4242 2023-03-08
    """
    time_start = time_start.astimezone(datetime.timezone.utc)
    time_stop = options['time_stop'].astimezone(datetime.timezone.utc)

    dump_dir = pathlib.Path(options['dump_dir'])
    query_frame_size = datetime.timedelta(seconds=options['query_frame_size'])
    prefix = options['metrics_prefix']
    need_remove_meta = options['remove_metadata']

    dump_dir.mkdir(exist_ok=True, parents=True)
    dump_frame = _TimeFrame(time_start, time_stop)

    rest_statistics = utils.RestStatistics()
    statistics = _DumpStatistics(rest_statistics)

    http_session = requests.Session()
    http_session.hooks['response'].append(utils.ResponseStatisticsHook(rest_statistics))

    client = stats_client.OpenTSDBStatsClient(http_session, opentsdb_endpoint)

    all_metrics_iterator = stats_client.OpenTSDBMetricsList(http_session, opentsdb_endpoint, prefix=prefix)
    for metric in all_metrics_iterator:
        dump(statistics, client, dump_frame, dump_dir, metric, query_frame_size, need_remove_meta=need_remove_meta)


def dump(statistics, client, dump_frame, dump_location, metric_name, query_frame_size, need_remove_meta):
    meta = _DumpMetadata(metric_name, dump_location)

    try:
        last_frame = meta.read()
        start = last_frame.start
    except ValueError:
        start = utils.datetime_align(dump_frame.start, query_frame_size)

    end = dump_frame.end

    query_manager = _AggDataQueryManager()
    statistics.evaluate_expected_iterations_count(start, end, query_frame_size)

    stream = build_time_stream(start, end, query_frame_size)
    stream = query_data_stream(stream, query_manager)
    stream = stats_stream(stream, client, metric_name, query_manager)

    dump_file = dump_location / (metric_name + '.ndjson')
    with dump_file.open('at') as target:
        if 0 < target.tell():
            # extending existing file, make sure we have line separator before
            # new record
            target.write('\n')
        with DumpProgressReport(metric_name, statistics) as status_report:
            _dump_stream(stream, target, meta, status_report, statistics)

    if need_remove_meta:
        meta.remove()


def _dump_stream(stream, target, meta, status_report, statistics):
    writer = ndjson.writer(target)
    for frame, stats_entries in stream:
        status_report.flush()
        for entry in stats_entries:
            statistics.add_entry(frame, entry)
            writer.writerow(mapping.encode_stats_entry(entry))

        meta.write(frame)

    status_report.flush()


def build_time_stream(start, end, step):
    factory = _FrameFactory()

    stream = time_stream(start, step)
    stream = finite_time_stream(stream, end)
    stream = frame_stream(stream)
    stream = frame_overlap_fix_stream(
        stream, end_offset=datetime.timedelta(seconds=-1))

    for frame_start, frame_end in stream:
        yield factory.produce(frame_start, frame_end)


def time_stream(start, step):
    now = start
    while True:
        yield now
        now += step


def finite_time_stream(stream, end):
    for now in stream:
        if end < now:
            break
        yield now


def frame_stream(stream):
    start = None
    for end in stream:
        if start is not None:
            yield start, end
        start = end


def frame_overlap_fix_stream(
        stream, start_offset=ZERO_TIMEDELTA, end_offset=ZERO_TIMEDELTA):
    for start, end in stream:
        yield start + start_offset, end + end_offset


def query_data_stream(stream, query_manager):
    while True:
        for entry in query_manager.flush():
            yield entry
        for frame in stream:
            yield _QueryData(frame)
            break
        else:
            break


def stats_stream(stream, client, metric, query_manager):
    for query_data in stream:
        tags = {x: '*' for x in query_data.agg_tags}
        stats_data = client.query_range(
            query_data.frame.start, query_data.frame.end,
            metric, tags=tags)

        try:
            batches = stats_data.lookup(metric)
        except ValueError:
            batches = []

        for entry in batches:
            if entry.aggregate_tags:
                query_manager.schedule(
                    _extract_stats_batch_time_frame(entry, query_data.frame),
                    entry.aggregate_tags)
                continue

            yield query_data.frame, stats_client.batch_to_entries(entry)


def _extract_stats_batch_time_frame(batch, fallback):
    if not batch.values:
        return fallback
    start = batch.values[0].timestamp
    end = batch.values[-1].timestamp
    if start == end:
        end = start + datetime.timedelta(seconds=1)

    return _TimeFrame(
        start, end, step_number=fallback.step_number)


class _DumpStatistics:
    def __init__(self, rest_statistics):
        self.rest = rest_statistics
        self.expected_iterations_count = 0
        self.entries_count = 0
        self.current_frame = None

        self._average_frame_time = _SlidingAverage(init=ZERO_TIMEDELTA)
        self._average_frame_entries_count = _SlidingAverage()
        self._average_frame_entries = 0

    def evaluate_expected_iterations_count(self, start, end, step):
        duration = end - start
        iterations = duration // step
        if duration % step:
            iterations += 1

        self.expected_iterations_count = iterations

    def add_entry(self, frame, entry):
        self.entries_count += 1

        if self.current_frame is None:
            self.current_frame = _DumpFrameStatistics(frame)

        if self.current_frame.update(frame, entry):
            return

        next_frame = _DumpFrameStatistics(frame)
        next_frame.update(frame, entry)

        delta = next_frame.seen_time - self.current_frame.seen_time

        self._average_frame_time.count(delta)
        self._average_frame_entries_count.count(
            self.current_frame.entries_count)

        self.current_frame = next_frame


class _DumpFrameStatistics:
    def __init__(self, frame):
        self.frame = frame
        self.seen_time = utils.time_now()

        self.entries_count = 0

    def update(self, frame, entry):
        if self.frame != frame:
            return False
        self.entries_count += 1
        return True


class _DumpMetadata(utils.OperationMetadata):
    def __init__(self, metric_name, dump_location):
        super().__init__(
            dump_location / (metric_name + '.dmeta.json'),
            dump_location / (metric_name + '.dmeta.json.upd'))

    def _encode(self, entry):
        return {
            'start': utils.datetime_to_unixtime(entry.start),
            'end': utils.datetime_to_unixtime(entry.end)}

    def _decode(self, data):
        start = utils.unixtime_to_datetime(data['start'])
        end = utils.unixtime_to_datetime(data['end'])
        return _TimeFrame(start, end)


class DumpProgressReport(report.ProgressReportBase):
    def __init__(self, metric_name, statistics, **kwargs):
        super().__init__(**kwargs)
        self._metric_name = metric_name
        self.statistics = statistics

    def _format_message(self):
        message = ['Dumping "', self._metric_name, '"']
        frame_statistics = self.statistics.current_frame
        if frame_statistics is not None:
            message.extend((
                ' at ', frame_statistics.frame.start,
                ' #', frame_statistics.frame.step_number))
        message.extend((
            ' ', self.statistics.entries_count, ' entries dumped total ',
            ' ', self.statistics.rest.requests_count, ' http requests'))
        return message


class _SlidingAverage:
    def __init__(self, entries=0, init=0):
        self.entries = entries
        self.total = init

    def count(self, value):
        self.total += value
        self.entries += 1

    def get_average(self):
        if not self.entries:
            return self.total
        return self.total / self.entries


class _AggDataQueryManager:
    def __init__(self):
        self._queue = []

    def schedule(self, frame, agg_tags):
        self._queue.append(_QueryData(frame, agg_tags))

    def flush(self):
        queue, self._queue = self._queue, []
        return queue


class _QueryData:
    __slots__ = ('frame', 'agg_tags')

    def __init__(self, frame, agg_tags=None):
        if not agg_tags:
            agg_tags = set()
        self.frame = frame
        self.agg_tags = agg_tags


class _TimeFrame:
    __slots__ = ('start', 'end', 'step_number')

    def __init__(self, start, end, step_number=0):
        self.start = start
        self.end = end
        self.step_number = step_number


class _FrameFactory:
    def __init__(self, step_number=0):
        self.step_now = step_number

    def produce(self, start, end):
        frame = _TimeFrame(start, end, self.step_now)
        self.step_now += 1
        return frame
