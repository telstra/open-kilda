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
import threading
import time
import json
from kafka import KafkaConsumer

KAFKA_GROUP_ID = 'wfm-consumer'
KAFKA_TOPIC = 'kilda-test'
KAFKA_CONSUMER_COUNT = 5

shutting_down = False

format = '%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d'
format += ':%(message)s'
logging.basicConfig(
    format=format,
    level=logging.INFO
)
logger = logging.getLogger(__name__)


class Consumer(threading.Thread):
    daemon = True

    def run(self):
        topics = [args.topic]
        consumer = KafkaConsumer(bootstrap_servers=args.server,
                                 auto_offset_reset='latest',
                                 group_id=KAFKA_GROUP_ID,
                                 api_version=(0, 10))
        consumer.subscribe(topics)

        try:
            while not shutting_down:
                message = consumer.next()
                if message:
                    logging.info("recevied: {}".format(message))
            consumer.close()
        except Exception as e:
            logging.error(e)
        finally:
            consumer.close()


def parse_cmdline():
    parser = argparse.ArgumentParser()
    parser.add_argument('server', action='store',
                        help='Kafka server:port.')
    parser.add_argument('topic', action='store',
                        help='Kafka topic to listen to')
    return parser.parse_args()

def main():
    Consumer().start()

    try:
        while True:
            time.sleep(3)
    except:
        logger.info("shutting down")
        shutting_down = True

if __name__ == "__main__":
    args = parse_cmdline()
    logging.info("bootstrap_servers = {}".format(args.server))
    main()
