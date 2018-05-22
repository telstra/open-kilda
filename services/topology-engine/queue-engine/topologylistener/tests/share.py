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

from topologylistener import flow_utils
from topologylistener import messageclasses
from topologylistener import message_utils


def exec_isl_discovery(isl, **fields):
    payload = payload_isl_info(isl, **fields)
    return messageclasses.MessageItem(**command(payload)).handle()


def payload_isl_info(isl, **fields):
    payload = {
        'state': 'DISCOVERED',
        'latency_ns': 20,
        'speed': 1000,
        'available_bandwidth': 1000}
    payload.update(fields)
    payload.update({
        'clazz': messageclasses.MT_ISL,
        'path': [
            {
                'switch_id': isl.source.dpid,
                'port_no': isl.source.port},
            {
                'switch_id': isl.dest.dpid,
                'port_no': isl.dest.port}]})

    return payload


def command(payload, **fields):
    message = {
        'timestamp': 0,
        'correlation_id': make_correlation_id('test')}
    message.update(fields)
    message.update({
        'clazz': message_utils.MT_INFO,
        'payload': payload})
    return message


def make_correlation_id(prefix=''):
    if prefix and prefix[-1] != '.':
        prefix += '.'
    return '{}{}'.format(prefix, uuid.uuid1())


class Environmnet(object):
    def __init__(self):
        self.init_logging()
        self.neo4j_connect = self.init_neo4j()

    def init_logging(self):
        logging.basicConfig(level=logging.DEBUG)

    def init_neo4j(self):
        return flow_utils.graph


env = Environmnet()
