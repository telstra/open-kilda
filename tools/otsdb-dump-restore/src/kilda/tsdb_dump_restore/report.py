# report.py

import sys


class ProgressReportBase:
    _last_len = 0

    def __init__(self, stream=None):
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

        self.close()

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

    def close(self):
        self.flush()
        if 0 < self._last_len:
            self.stream.write('\n')
            self.stream.flush()

    def _format_message(self):
        raise NotImplementedError()
