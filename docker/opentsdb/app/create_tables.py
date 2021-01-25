#!/usr/bin/python
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


import happybase
import sys

host = 'hbase.pendev'
port = 9090

new_tables = ['tsdb', 'tsdb-uid', 'tsdb-tree', 'tsdb-meta']

connection = happybase.Connection(host=host, port=port)
existing_tables = connection.tables()


def create_table(table):
    if (table == 'tsdb') or (table == 'tsdb-tree'):
        families = {'t': dict(max_versions=1, compression='none',
                              bloom_filter_type='ROW')}
    elif table == 'tsdb-uid':
        families = {'id': dict(compression='none', bloom_filter_type='ROW'),
                    'name': dict(compression='none', bloom_filter_type='ROW')}
    elif table == 'tsdb-meta':
        families = {'name': dict(compression='none', bloom_filter_type='ROW')}
    else:
        sys.exit("Unknown table {} was requested.".format(table))

    print("Creating %s" % table)
    connection.create_table(table, families)
    if bytes(table, 'utf-8') not in connection.tables():
        sys.exit("Could not create {}".format(table))


for table in new_tables[:]:
    if table in existing_tables:
        print("%s exist" % table)
        new_tables.remove(table)

if len(new_tables) > 0:
    for table in new_tables:
        create_table(table)
else:
    print("All OpenTSDB tables already created")
