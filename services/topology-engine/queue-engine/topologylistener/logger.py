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

import sys
import logging

__all__ = ['get_logger']

FORMATTER = '%(asctime)23s | %(name)s [%(threadName)s] | %(levelname)-5s  | ' \
            '%(filename)s:%(lineno)d [%(funcName)s] | %(message)s'

root_logger = logging.getLogger("queue-engine")
root_logger.setLevel(logging.DEBUG)

kazoo_client = logging.getLogger("kazoo.client")
kazoo_client.setLevel(logging.DEBUG)

ch = logging.StreamHandler(sys.stdout)
ch.setLevel(logging.INFO)

formatter = logging.Formatter(FORMATTER)
ch.setFormatter(formatter)

root_logger.addHandler(ch)
kazoo_client.addHandler(ch)


def get_logger():
    return root_logger
