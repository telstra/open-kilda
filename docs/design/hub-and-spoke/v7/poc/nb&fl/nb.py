from gevent import monkey
monkey.patch_all()  # noqa
import gevent
from gevent.pywsgi import WSGIServer
from gevent.queue import Queue, Empty

import uuid
import logging

from kafka import KafkaConsumer
from kafka import KafkaProducer
from kafka import TopicPartition


from flask import Flask
from flask import jsonify
from flask import request

app = Flask(__name__)
logging.basicConfig(format='%(levelname)s: %(message)s', level=logging.INFO)
logger = logging.getLogger(__name__)

queues = {}


def kafka_recive_loop():
    consumer = KafkaConsumer(bootstrap_servers=['localhost:9092'],
                             group_id="nb",
                             auto_offset_reset='latest',
                             key_deserializer=bytes.decode,
                             value_deserializer=bytes.decode,
                             enable_auto_commit=True)
    consumer.subscribe(['hub-to-nb'])
    while True:
        r = consumer.poll(0, 1)
        if r:
            message = r[TopicPartition(topic='hub-to-nb', partition=0)][0]
            queue = queues.get(message.key)
            if queue:
                queue.put(message)
            else:
                logger.warning('miss response for %s', message.key)
        gevent.sleep(0.1)


producer = KafkaProducer(bootstrap_servers='localhost:9092',
                         key_serializer=str.encode,
                         value_serializer=str.encode)


@app.route('/flow', methods=['POST'])
def flow_create():
    request_id = uuid.uuid4()
    try:
        queue = Queue()
        queues[request_id.hex] = queue
        payload = request.get_json()
        producer.send('nb-to-hub', key=request_id.hex,
                      value=str(payload['length']))
        producer.flush()
        result = queue.get(block=True, timeout=10)

        return result.value
    except Empty:
        return jsonify(error='client operation timeout')
    finally:
        del queues[request_id.hex]


if __name__ == "__main__":
    logger.info('Start kafka loop...')
    gevent.spawn(kafka_recive_loop)
    logger.info('Serving on 8088...')
    WSGIServer(('', 8088), app).serve_forever()


# docker run --rm --net=host landoop/fast-data-dev
