#!/usr/bin/python

import happybase
import sys

host = 'hbase.pendev'
port = 9090

new_tables = [ 'tsdb', 'tsdb-uid', 'tsdb-tree', 'tsdb-meta']

connection = happybase.Connection(host=host, port=port)
existing_tables = connection.tables()

def create_table(table):
    if (table == 'tsdb') or (table == 'tsdb-tree'):
        families = {'t': dict(max_versions=1, compression='none', bloom_filter_type='ROW')}
    elif table == 'tsdb-uid':
        families = {'id': dict(compression='none', bloom_filter_type='ROW'),
                    'name': dict(compression='none', bloom_filter_type='ROW')}
    elif table == 'tsdb-meta':
        families = {'name': dict(compression='none', bloom_filter_type='ROW')}
    else:
        sys.exit("Unknown table {} was requested.".format(table))

    print "Creating {}".format(table)
    connection.create_table(table, families)
    if table not in connection.tables():
        sys.exit("Could not create {}".format(table))


for table in new_tables[:]:
    if table in existing_tables:
        print "{} exist".format(table)
        new_tables.remove(table)

if len(new_tables) > 0:
    for table in new_tables:
        create_table(table)
else:
    print "All OpenTSDB tables already created"
