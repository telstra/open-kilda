
import logging

from kafka import KafkaConsumer
from kafka import KafkaProducer

from flask import Flask

app = Flask(__name__)
logging.basicConfig(format='%(levelname)s: %(message)s', level=logging.INFO)
logger = logging.getLogger(__name__)

queues = {}

producer = KafkaProducer(bootstrap_servers='localhost:9092',
                         key_serializer=str.encode,
                         value_serializer=str.encode)


def kafka_recive_loop():
    consumer = KafkaConsumer(bootstrap_servers=['localhost:9092'],
                             group_id="fl",
                             auto_offset_reset='latest',
                             key_deserializer=bytes.decode,
                             value_deserializer=bytes.decode)
    consumer.subscribe(['worker-to-fl'])
    for message in consumer:
        logger.info(message)
        if "-1" in message.value:
            logger.error("invalid operation id")
        else:
            producer.send('fl-to-worker', key=message.key,
                          value='processed {}'.format(message.value))


if __name__ == "__main__":
    logger.info('Start kafka loop...')
    kafka_recive_loop()

