#

import errno
import os
import pathlib
import signal
import tempfile
import time


class FileAtomicPut(object):
    _is_active = False
    stream = None

    def __init__(self, target, mode=None, tempdir=None, text_io=False):
        self.target = target
        self.mode = self._precise_mode(mode)
        if tempdir is None:
            tempdir = self.target.parent
        self.tempdir = str(tempdir)
        self.text_io = text_io

    def __enter__(self):
        if self._is_active:
            raise RuntimeError('{} is not re-enterable'.format(self))
        self._is_active = True

        extra = {}
        if self.text_io:
            extra['mode'] = 'w+'

        self.stream = tempfile.NamedTemporaryFile(
                prefix='.{}-'.format(self.target.name), suffix='.tmp',
                dir=self.tempdir, delete=False, **extra)
        return self.stream

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_val:
            self._revoke()
        else:
            self._submit()

    def _precise_mode(self, mode):
        if mode is not None:
            return mode
        try:
            stat = self.target.stat()
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise
            mode = None
        else:
            mode = stat.st_mode & 0o777
        return mode

    def _submit(self):
        tmp = pathlib.Path(self.stream.name)
        if self.mode is not None:
            os.fchmod(self.stream.fileno(), self.mode)
        tmp.rename(self.target)

    def _revoke(self):
        pathlib.Path(self.stream.name).unlink()


class TimeDelayTracker(object):
    def __init__(self, interval):
        if interval <= 0:
            raise ValueError('"interval" must be positive float number')

        self.left = self.interval = interval
        self._last = time.monotonic()

    def __call__(self):
        now = time.monotonic()
        next_tick = self._last + self.interval

        if next_tick < now:
            result = True
            self._last = self.interval * int(now / self.interval)
            self.left = next_tick - self._last
        else:
            result = False
            self.left = next_tick - now

        return result

    def reset(self):
        self._last = time.monotonic()
        self.left = self.interval


class WorkerState(object):
    _term = False
    _tick = False

    def register_term(self):
        self._term = True

    def register_tick(self):
        self._tick = True

    def read_term(self):
        return self._term

    def read_tick(self):
        result, self._tick = self._tick, False
        return result


class AbstractSignal(object):
    def __init__(self, signum, state):
        self.state = state
        self.signum = signum
        self.replaced_handler = signal.signal(self.signum, self)

    def __call__(self, signum, frame):
        self.handle()

    def handle(self):
        raise NotImplementedError


class TermSignal(AbstractSignal):
    def __init__(self, state):
        super().__init__(signal.SIGTERM, state)

    def handle(self):
        self.state.register_term()
