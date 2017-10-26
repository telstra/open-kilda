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

import gevent
import kazoo.client

import config

kazoo_client = kazoo.client.KazooClient(config.ZOOKEEPER_HOSTS)
kazoo_client.start()


def get_isl_lock(switch_src, port_src):
    lock_path = '/isl/{}_{}'.format(switch_src, port_src)
    return kazoo_client.Lock(lock_path, hex(id(gevent.getcurrent())))


def get_flow_lock(flow_id):
    lock_path = '/flow/{}'.format(flow_id)
    return kazoo_client.Lock(lock_path, hex(id(gevent.getcurrent())))
