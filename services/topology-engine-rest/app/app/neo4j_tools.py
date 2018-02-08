from __future__ import absolute_import

import ConfigParser
import logging
import os
import time

import py2neo.database
import py2neo.database.status
from py2neo import Graph

from . import exc

logger = logging.getLogger(__name__)


class RetryConfig(object):
    iteration = 0
    max_attempts = 3
    delay = 0.1

    @classmethod
    def configure(cls, config):
        for attr, conv in (
                ('max_attempts', int),
                ('delay', float)):
            key = 'retry.' + attr
            try:
                value = config.get('neo4j', key)
                value = conv(value)
            except (ConfigParser.NoOptionError, ConfigParser.NoSectionError):
                continue
            except TypeError as e:
                raise exc.ConfigError('neo4j', key, e)

            setattr(cls, attr, value)

    def can_retry(self):
        if self.max_attempts < 0:
            return True
        return self.iteration < self.max_attempts

    def sleep(self):
        self.iteration += 1
        if 0 < self.delay:
            time.sleep(self.delay)


class BoltTransactionPatch(py2neo.database.BoltTransaction):
    def run(self, statement, parameters=None, **kwparameters):
        retry = RetryConfig()

        while retry.can_retry():
            try:
                result = super(BoltTransactionPatch, self).run(
                        statement, parameters, **kwparameters)
            except py2neo.database.status.TransientError:
                if not self.autocommit:
                    raise
                retry.sleep()
                continue

            return result


def connect(config):
    RetryConfig.configure(config)

    env_keys_map = {
        'host': 'neo4host',
        'login': 'neo4usr',
        'password': 'neo4pass'
    }

    config_keys_map = {
        'login': 'user',
        'password': 'pass'
    }
    config_section = 'neo4j'

    args = {}

    for option in ('host', 'login', 'password'):
        name = env_keys_map.get(option, option)
        value = os.environ.get(name)
        if not value:
            name = config_keys_map.get(option, option)
            value = config.get(config_section, name)

        args[option] = value

    uri_format = "http://{login}:{password}@{host}:7474/db/data/".format
    uri = uri_format(**args)

    args['password'] = '*' * 5
    logger.info('NEO4j connect %s', uri_format(**args))
    return Graph(uri)


# monkeypatching py2neo.database to inject our retry mechanism
py2neo.database.BoltTransaction = BoltTransactionPatch
