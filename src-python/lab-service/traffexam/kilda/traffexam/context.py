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

import logging.config
import os
import pathlib

from kilda.traffexam import const
from kilda.traffexam import common
from kilda.traffexam import exc


class Context(object):
    is_debug = False

    root = os.path.join(os.sep, 'var', 'run', const.PROJECT_NAME)
    service = None
    action = None
    _init_done = False

    def __init__(self, iface, rest_bind):
        self.iface = iface
        self.rest_bind = rest_bind

        self.children = common.ProcMonitor()
        self.shared_registry = common.Registry()
        self._acquired_resources = []

        self.set_root(self.root)
        self.set_debug_mode(self.is_debug)

        self._init_done = True

    def close(self):
        for resource in self._acquired_resources:
            resource.release()

    def path(self, *chunks):
        return self.root.joinpath(*chunks)

    def acquire_resources(self, *resources):
        for allocator in resources:
            self._acquired_resources.insert(0, allocator(self))

    def make_lock_file_name(self):
        name = '{}-{}.lock'.format(self.iface.name, self.iface.index)
        return str(self.path(name))

    def make_network_namespace_name(self):
        return '{}{}.{}'.format(
                const.IF_PREFIX, self.iface.name, self.iface.index)

    def make_bridge_name(self):
        return '{}gw.{}'.format(const.IF_PREFIX, self.iface.index)

    def make_veth_base_name(self):
        return '{}nsgw.{}'.format(const.IF_PREFIX, self.iface.index)

    def set_root(self, root):
        self.root = pathlib.Path(root)
        if not self._init_done:
            os.makedirs(str(self.root), exist_ok=True)
        return self

    def set_default_logging(self):
        stderr = logging.StreamHandler()
        stderr.setFormatter('%(asctime)s %(levelname)s %(name)s - %(message)s')

        log = logging.getLogger()
        log.addHandler(stderr)
        log.setLevel(logging.INFO)
        return self

    def set_logging_config(self, config, incremental=False):
        try:
            logging.config.fileConfig(
                config, disable_existing_loggers=not incremental)
        except (IOError, OSError) as e:
            raise exc.InvalidLoggingConfigError(config, e)
        return self

    def set_debug_mode(self, mode):
        self.is_debug = mode
        return self

    def set_service_adapter(self, adapter):
        self.service = adapter
        return self

    def set_action_adapter(self, adapter):
        self.action = adapter
        return self


class ContextConsumer(object):
    def __init__(self, context):
        self.context = context
