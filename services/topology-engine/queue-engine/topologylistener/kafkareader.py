from kafka import KafkaConsumer
import time
import os

from logger import get_logger

logger = get_logger()
logger.info('Connecting to kafka using application defined configuration')


def create_consumer():
    bootstrap_server = os.environ['bootstrapserver']
    topic = os.environ['topic']
    group = os.environ['group']

    while True:
        try:
            consumer = KafkaConsumer(bootstrap_servers=bootstrap_server,
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
