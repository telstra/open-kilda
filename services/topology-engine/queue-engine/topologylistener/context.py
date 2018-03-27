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
import uuid

from topologylistener import const
from topologylistener import log as log_module

raw_logger = logging.getLogger(__name__)


class OperationContext(object):
    CORRELATION_ID_FIELD = 'correlation_id'

    invalid_correlation_ids = {
        'system', 'admin-request', 'system-request'}

    def __init__(self, record):
        self.correlation_id = self._fetch_correlation_id(record)

    def log(self, target):
        return self._log_adapter(target, self.correlation_id)

    def _fetch_correlation_id(self, record):
        try:
            correlation_id = record[self.CORRELATION_ID_FIELD]
            if correlation_id in self.invalid_correlation_ids:
                raise ValueError(correlation_id)
        except (KeyError, ValueError) as e:
            correlation_id = str(uuid.uuid1())
            log = self._log_adapter(raw_logger, correlation_id)
            log.warning('Generate new correlation ID: %s', correlation_id)

            if isinstance(e, KeyError):
                log.error('Got request without %r field',
                          self.CORRELATION_ID_FIELD)
            else:
                log.error(
                        'Got request with invalid value %r in %r field.',
                        e.args[0], self.CORRELATION_ID_FIELD)

        return correlation_id

    @staticmethod
    def _log_adapter(target, correlation_id):
        extra = {
            const.LOG_ATTR_CORRELATION_ID: correlation_id}
        return log_module.LoggerAdapter(target, extra)
