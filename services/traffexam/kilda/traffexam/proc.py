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

import errno
import fcntl
import os
import time

from kilda.traffexam import common
from kilda.traffexam import exc


class PidFile(common.Resource):
    def __init__(self, path):
        self.path = path

    def update(self):
        with self._open() as pid_file:
            self._ensure_owning(pid_file)
            self._write(pid_file)

    def acquire(self):
        with self._open() as pid_file:
            pid = self._read(pid_file)
            if pid is not None and self.ping_proc(pid):
                raise exc.PidFileBusyError(self.path, pid)
            self._write(pid_file)

    def release(self):
        with self._open() as pid_file:
            self._ensure_owning(pid_file)
            try:
                os.unlink(self.path)
            except OSError as e:
                raise exc.PidFileError(self.path) from e

    def ping_proc(self, pid):
        try:
            os.kill(pid, 0)
            return True
        except OSError as e:
            if e.errno != errno.ESRCH:
                raise exc.PidFileError(self.path) from e

        return False

    def _open(self):
        for attempt in range(5):
            pid_fd = None
            try:
                pid_fd = os.open(self.path, os.O_RDWR | os.O_CREAT)
                fcntl.flock(pid_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as e:
                if pid_fd is not None:
                    os.close(pid_fd)
                if e.errno != errno.EWOULDBLOCK:
                    raise exc.PidFileError(self.path) from e

                time.sleep(0.1)
                continue

            break
        else:
            raise exc.PidFileLockError(self.path)

        return os.fdopen(pid_fd, 'w+t')

    def _read(self, pid_file):
        pid = None
        try:
            raw = pid_file.read(1024)
            if raw:
                pid = int(raw, 10)
        except (IOError, ValueError) as e:
            raise exc.PidFileError(self.path) from e
        return pid

    def _write(self, pid_file):
        try:
            pid_file.seek(0)
            pid_file.write('{}\n'.format(os.getpid()))
            pid_file.close()
        except IOError as e:
            raise exc.PidFileError(self.path) from e

    def _ensure_owning(self, pid_file):
        pid = self._read(pid_file)
        if pid != os.getpid():
            raise exc.PidFileStolenError(self.path, pid)
