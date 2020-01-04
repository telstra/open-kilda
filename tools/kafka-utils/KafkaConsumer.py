#!/usr/bin/env python
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
import argparse
import uuid
from kafka import KafkaConsumer

KAFKA_BOOTSTRAP_SERVERS = 'hadoop01.staging.pen:6667'


def parse_cmdline():
    parser = argparse.ArgumentParser()
    parser.add_argument('server', action='store', help='Kafka server:port.')
    parser.add_argument('topic', action='store', help='Kafka topic to listen on.')
    return parser.parse_args()


def main(args):
    group_uuid = uuid.uuid4()
    consumer = KafkaConsumer(bootstrap_servers=args.server,
                             auto_offset_reset='latest',
                             group_id=group_uuid,
                             api_version=(0, 9))
    topics = [args.topic]
    consumer.subscribe(topics)

    for message in consumer:
        print (message)


if __name__ == "__main__":
    logging.basicConfig(
        format='%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d:%(message)s',
        level=logging.INFO
    )
    logger = logging.getLogger(__name__)
    args=parse_cmdline()
    main(args)
