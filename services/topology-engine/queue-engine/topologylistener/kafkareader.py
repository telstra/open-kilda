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

import datetime
import os
import time
import logging

from kafka import KafkaConsumer

logger = logging.getLogger(__name__)


def create_consumer(config):
    group = os.environ.get('group', config.get('kafka', 'consumer.group'))
    topic = config.KAFKA_TOPO_ENG_TOPIC

    bootstrap_servers = config.KAFKA_BOOTSTRAP_SERVERS

    logger.info('Connecting to kafka: group=%s, topic=%s, '
                'bootstrap_servers=%s', group, topic, str(bootstrap_servers))

    while True:
        try:
            consumer = KafkaConsumer(bootstrap_servers=bootstrap_servers,
                                     group_id=group,
                                     auto_offset_reset='earliest')
            consumer.subscribe(['{}'.format(topic)])
            logger.info('Connected to kafka')
            return consumer

        except Exception as e:
            logger.exception('Can not connect to Kafka: %s', e.message)
            time.sleep(5)


def read_message(consumer):
    kafka_record = consumer.next()

    record_raw_time = float(kafka_record.timestamp) / 1000
    record_time = datetime.datetime.fromtimestamp(record_raw_time)
    logger.debug(
            'Fetch message (partition: %s, offset: %s, timestamp: %.03f (%s), '
            'timestamp-parsed: %s, size: %d)',
            kafka_record.partition, kafka_record.offset, record_raw_time,
            kafka_record.timestamp_type,
            record_time.strftime('%Y.%m.%d %H:%M:%S.%f'),
            len(kafka_record.value))

    return kafka_record.value
