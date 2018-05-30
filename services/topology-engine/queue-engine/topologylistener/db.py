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
import os
import socket

import py2neo

from topologylistener import config
from topologylistener import exc


def create_p2n_driver():
    graph = py2neo.Graph("http://{}:{}@{}:7474/db/data/".format(
        os.environ.get('neo4juser') or config.get('neo4j', 'user'),
        os.environ.get('neo4jpass') or config.get('neo4j', 'pass'),
        os.environ.get('neo4jhost') or config.get('neo4j', 'host')))
    return graph


def neo_id(db_object):
    return py2neo.remote(db_object)._id


def format_set_fields(payload, field_prefix=''):
    return format_fields(payload, 'SET ', field_prefix=field_prefix)


def format_fields(payload, prefix, field_prefix=''):
    if not payload:
        return ''

    separator = ',\n' + (' ' * len(prefix)) + field_prefix
    fields = [' = '.join(field) for field in payload]
    return prefix + field_prefix + separator.join(fields)


def escape_fields(payload, raw_values=False):
    result = []
    for field, value in payload.items():
        if not raw_values:
            value = py2neo.cypher_repr(value)
        result.append(
                (py2neo.cypher_escape(field), value))
    return result


def fetch_one(cursor):
    extra = result_set = None
    try:
        result_set = next(cursor)
        extra = next(cursor)
    except StopIteration:
        if result_set is None:
            raise exc.DBEmptyResponse

    if extra is not None:
        raise exc.DBMultipleResponse

    return result_set


class ResponseIterator(collections.Iterator):
    is_first = True

    def __init__(self, cursor, q, p):
        self.cursor = cursor
        self.exc_empty = exc.DBRecordNotFound(q, p)

    def next(self):
        try:
            value = next(self.cursor)
        except StopIteration:
            if self.is_first:
                raise self.exc_empty
            raise StopIteration
        finally:
            self.is_first = False

        return value

    def __iter__(self):
        return self


# neo4j monkey patching staff

dummy = object()


def create_connection(address, timeout=dummy, source_address=None):
    if timeout is dummy:
        timeout = config.NEO4J_SOCKET_TIMEOUT
    return socket.create_connection(
        address, timeout, source_address=source_address)


# Remove endless hang after Neo4j disconnect
import py2neo.packages.neo4j.v1.bolt
py2neo.packages.neo4j.v1.bolt.create_connection = create_connection
