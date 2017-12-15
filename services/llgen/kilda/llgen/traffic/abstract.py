#

import errno
import logging

from kilda.llgen import context as llgen_context
from kilda.llgen import protocol
from kilda.llgen import utils

logging_name = llgen_context.logging_name('traffic.{}').format


class AbstractModule(object):
    def __init__(self):
        self.children = {}
        self.log = logging.getLogger(logging_name('').rstrip('.'))

    def __call__(self, item_data):
        kind = type(item_data)
        if kind is protocol.ProducerItem:
            result = self.producer(item_data)
        elif kind is protocol.ConsumerItem:
            result = self.consumer(item_data)
        elif kind is protocol.DropItem:
            result = self.drop(item_data)
        elif kind is protocol.ListItem:
            result = self.list(item_data)
        elif kind is protocol.StatsItem:
            result = self.stats(item_data)
        elif kind is protocol.TimeToLiveItem:
            result = self.ttl_renew(item_data)
        else:
            raise self.InvalidItemKindError

        return result

    def timer_housekeeping(self):
        for child in self.children.values():
            if child.exitcode is not None:
                continue
            child.join(0)

    def term_request(self):
        for child in self.children.values():
            self.log.debug('Send TERM to %s', child.pid)
            self._terminate(child)

    def producer(self, item):
        raise NotImplementedError

    def consumer(self, item):
        raise NotImplementedError

    def list(self, item):
        raise NotImplementedError

    def drop(self, item):
        raise NotImplementedError

    def stats(self, item):
        raise NotImplementedError

    def ttl_renew(self, item):
        raise NotImplementedError

    @staticmethod
    def _terminate(child):
        if child.exitcode is None:
            child.terminate()
            child.join()

    class InvalidItemKindError(Exception):
        pass


class Worker(object):
    _timer_interval = 1.0

    def __init__(self):
        self._state = utils.WorkerState()

    def __call__(self):
        utils.TermSignal(self._state)
        interval = utils.TimeDelayTracker(self._timer_interval)

        while not self._state.read_term():
            try:
                is_interval_end = interval()
                self.main_loop(interval.left)

                if is_interval_end:
                    self.timer_event_handler()
            except (OSError, IOError) as e:
                if e.errno != errno.EINTR:
                    raise

    def main_loop(self, time_frame):
        raise NotImplementedError

    def timer_event_handler(self):
        pass
