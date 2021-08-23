#  Copyright 2021 Telstra Open Source
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

import collections
import datetime

import click
import mysql.connector
import pyorient

from . import model
from . import orphaned
from . import pull_mysql
from . import pull_orient
from . import push


@click.group()
def cli():
    pass


@cli.command('pull-orientdb')
@click.option('--orient-db-host', default='localhost')
@click.option('--orient-db-port', type=int, default=2424)
@click.option('--orient-db-login', default='kilda')
@click.option('--orient-db-password', default='')
@click.option('--time-start', type=click.types.DateTime())
@click.option('--time-stop', type=click.types.DateTime())
@click.option('--skip-flow-events', is_flag=True)
@click.option('--skip-port-events', is_flag=True)
@click.argument('database')
@click.argument('stream_out', type=click.types.File(mode='wt', lazy=True))
def history_pull_orientdb(database, stream_out, **options):
    auth = _LoginPassword(
        options['orient_db_login'], options['orient_db_password'])
    orient_client = _make_orientdb_client(
        options['orient_db_host'], options['orient_db_port'], database, auth)
    targets = _make_pull_targets(options)
    time_range = _make_time_range(options['time_start'], options['time_stop'])
    pull_orient.orient_to_ndjson(orient_client, stream_out, targets, time_range)


@cli.command('pull-mysql')
@click.option('--mysql-db-host', default='localhost')
@click.option('--mysql-db-port', type=int, default=3306)
@click.option('--mysql-db-login', default='kilda')
@click.option('--mysql-db-password', default='')
@click.option('--time-start', type=click.types.DateTime())
@click.option('--time-stop', type=click.types.DateTime())
@click.option('--skip-flow-events', is_flag=True)
@click.option('--skip-port-events', is_flag=True)
@click.argument('database')
@click.argument('stream_out', type=click.types.File(mode='wt', lazy=True))
def history_pull_mysql(database, stream_out, **options):
    auth = _LoginPassword(
        options['mysql_db_login'], options['mysql_db_password'])
    mysql_client = _make_mysql_client(
        options['mysql_db_host'], options['mysql_db_port'], database, auth)
    targets = _make_pull_targets(options)
    time_range = _make_time_range(options['time_start'], options['time_stop'])
    pull_mysql.mysql_to_ndjson(mysql_client, stream_out, targets, time_range)


@cli.command('push-orientdb')
@click.option('--orient-db-host', default='localhost')
@click.option('--orient-db-port', type=int, default=2424)
@click.option('--orient-db-login', default='kilda')
@click.option('--orient-db-password', default='')
@click.argument('stream_in', type=click.types.File(mode='rt', lazy=False))
@click.argument('database')
def history_push_orientdb(stream_in, database, **options):
    auth = _LoginPassword(
        options['orient_db_login'], options['orient_db_password'])
    orient_client = _make_orientdb_client(
        options['orient_db_host'], options['orient_db_port'], database, auth)
    push.ndjson_to_orientdb(orient_client, stream_in)


@cli.command('push-mysql')
@click.option('--mysql-db-host', default='localhost')
@click.option('--mysql-db-port', type=int, default=3306)
@click.option('--mysql-db-login', default='kilda')
@click.option('--mysql-db-password', default='')
@click.argument('stream_in', type=click.types.File(mode='rt', lazy=False))
@click.argument('database')
def history_push_mysql(stream_in, database, **options):
    auth = _LoginPassword(
        options['mysql_db_login'], options['mysql_db_password'])
    mysql_client = _make_mysql_client(
        options['mysql_db_host'], options['mysql_db_port'], database, auth)
    push.ndjson_to_mysql(mysql_client, stream_in)


@cli.command('count-orphaned')
@click.option('--orient-db-host', default='localhost')
@click.option('--orient-db-port', type=int, default=2424)
@click.option('--orient-db-login', default='kilda')
@click.option('--orient-db-password', default='')
@click.argument('database')
@click.argument('stream_out', type=click.types.File(mode='wt', lazy=True))
def history_orphaned(database, stream_out, **options):
    auth = _LoginPassword(
        options['orient_db_login'], options['orient_db_password'])
    orient_client = _make_orientdb_client(
        options['orient_db_host'], options['orient_db_port'], database, auth)
    orphaned.count_history_orphaned_record(orient_client, stream_out)


def main():
    """
    Entry point defined in setup.py
    """
    cli()


def _make_pull_targets(options):
    targets = set(model.PullTarget)
    for name, entry in (
            ('skip_flow_events', model.PullTarget.HISTORY_FLOW_EVENT),
            ('skip_port_events', model.PullTarget.HISTORY_PORT_EVENT)):
        if options[name]:
            targets.remove(entry)
    return targets


def _make_orientdb_client(hostname, port, database, auth):
    connect = {
        'host': hostname,
        'port': port}
    connect = {k: v for k, v in connect.items() if v is not None}
    client = pyorient.OrientDB(**connect)
    client.db_open(database, *auth)
    return client


def _make_mysql_client(hostname, port, database, auth):
    return mysql.connector.connect(
        host=hostname, port=port,
        user=auth.login, password=auth.password,
        database=database)


def _make_time_range(start, stop):
    start, stop = [_normalize_datetime(x) for x in (start, stop)]
    return _DateTimeRange(start, stop)


def _normalize_datetime(value):
    if not value:
        return None
    value_local_tz = value.astimezone(None)
    value_utc_tz = value_local_tz.astimezone(datetime.timezone.utc)
    return value_utc_tz


_LoginPassword = collections.namedtuple('_LoginPassword', ('login', 'password'))
_DateTimeRange = collections.namedtuple('_DateTimeRange', ('start', 'stop'))
