# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import collections
import signal
import weakref

from kilda.traffexam import exc


class Resource(object):
    _nesting_level = 0

    def __enter__(self):
        if not self._nesting_level:
            self.acquire()
        self._nesting_level += 1
        return self

    def __exit__(self, *exc_info):
        self._nesting_level -= 1
        if not self._nesting_level:
            self.release()

    def acquire(self):
        raise NotImplementedError

    def release(self):
        raise NotImplementedError


class Registry(object):
    def __init__(self):
        self._data = {}

    def add(self, klass, value):
        self._data[klass] = value

    def fetch(self, klass, remove=False):
        try:
            if remove:
                value = self._data.pop(klass)
            else:
                value = self._data[klass]
        except KeyError:
            raise exc.RegistryLookupError(self, klass)
        return value


class ProcMonitor(collections.Iterable):
    def __init__(self):
        self.proc_set = weakref.WeakSet()

    def __iter__(self):
        return iter(self.proc_set)

    def add(self, proc):
        self.proc_set.add(proc)

    def check_status(self):
        for proc in self.proc_set:
            proc.poll()


class AbstractSignal(object):
    def __init__(self, signum):
        self.signum = signum
        self.replaced_handler = signal.signal(self.signum, self)

    def __call__(self, signum, frame):
        self.handle()

    def handle(self):
        raise NotImplementedError
