import logging
import os

from py2neo import Graph

logger = logging.getLogger(__name__)


def connect(config):
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
