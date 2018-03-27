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

import logging
import json

from topologylistener import const


class LoggerAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        kwargs.setdefault('extra', dict()).update(self.extra)
        return msg, kwargs


class ContextAwareFormatter(logging.Formatter):
    missing = object()

    def format(self, record):
        format_fields = self._grab_fields(record, const.LOG_ALL_FORMAT_ATTR)
        tail_fields = self._grab_fields(record, const.LOG_ALL_TAIL_ATTR)

        original = getattr(record, const.LOG_FORMAT_KEY, self.missing)
        setattr(record, const.LOG_FORMAT_KEY, self._serialize(format_fields))
        try:
            message = super(ContextAwareFormatter, self).format(record)
        finally:
            if original is not self.missing:
                setattr(record, const.LOG_FORMAT_KEY, original)
            else:
                delattr(record, const.LOG_FORMAT_KEY)

        if tail_fields:
            tail = [
                '{} - {}'.format(field, self._serialize(value, pretty=True))
                for field, value in tail_fields.items()]

            tail.insert(0, 'Extra fields:')
            tail.insert(0, message.rstrip())
            message = '\n'.join(tail)

        return message

    @staticmethod
    def _grab_fields(record, fields):
        results = {}
        for attr in fields:
            try:
                results[attr] = getattr(record, attr)
            except AttributeError:
                pass
        return results

    @staticmethod
    def _serialize(record, pretty=False):
        if isinstance(record, (str, bytes)):
            return record

        extra = {}
        if pretty:
            extra['sort_keys'] = True
            extra['indent'] = 2
        try:
            encoded = json.dumps(record, **extra)
        except (TypeError, ValueError):
            encoded = repr(record)

        return encoded
