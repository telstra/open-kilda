#!/usr/bin/env python
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
    logger.info("Subscribing to {}".format(args.topic))
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