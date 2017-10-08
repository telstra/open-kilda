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

import os
import time

from kafka import KafkaConsumer
import ConfigParser

from logger import get_logger


config = ConfigParser.RawConfigParser()
config.read('topology_engine.properties')

group = config.get('kafka', 'consumer.group')
topic = config.get('kafka', 'topology.topic')
bootstrap_servers_property = config.get('kafka', 'bootstrap.servers')
bootstrap_servers = [x.strip() for x in bootstrap_servers_property.split(',')]

logger = get_logger()
logger.info('Connecting to kafka: group=%s, topic=%s, bootstrap_servers=%s',
            group, topic, str(bootstrap_servers))


def create_consumer():
    group = os.environ['group']

    while True:
        try:
            consumer = KafkaConsumer(bootstrap_servers=bootstrap_servers,
                                     group_id=group,
                                     auto_offset_reset='earliest')
            consumer.subscribe(['{}'.format(topic)])
            logger.info('Connected to kafka')
            break

        except Exception as e:
            logger.exception('Can not connect to Kafka: %s', e.message)
            time.sleep(5)

    return consumer


def read_message(consumer):
    try:
        message = consumer.next()
        if message.value is not "":
            return message.value
        else:
            logger.debug('sleeping')
            time.sleep(1)

    except Exception as e:
        logger.exception('Can not read message: %s', e.message)
