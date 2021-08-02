#  Copyright 2021 Telstra Open Source
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

import sys


class StatusOutputBase:
    _last_len = 0

    def __init__(self, prefix, stream=None):
        self.prefix = prefix

        if stream is None:
            stream = sys.stdout
        self.stream = stream

        self._nested_level = 0

    def __enter__(self):
        self._nested_level += 1
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._nested_level -= 1
        if 0 < self._nested_level:
            return

        self._close()

    def flush(self):
        chunks = self._format_message()
        chunks = (str(x) for x in chunks if x is not None)
        chunks = (str(x) for x in chunks if x)
        message = ''.join(chunks)
        if not message:
            return
        actual_len = len(message)
        message += ' ' * max(self._last_len - len(message), 0)
        try:
            if self._last_len:
                self.stream.write('\r')
            self.stream.write(message)
            self.stream.flush()
        finally:
            self._last_len = actual_len

    def _format_message(self):
        chunks = [self.prefix]
        if self.prefix:
            chunks.append(' ')
        return chunks

    def _close(self):
        self.flush()
        if 0 < self._last_len:
            self.stream.write('\n')
            self.stream.flush()


class StreamEntryCounter:
    def __init__(self):
        self._count = 0
        self._last = None

    def update(self, entry):
        self._last = entry
        self._count += 1

    def current(self):
        return self._count

    def last_entry(self):
        return self._last
