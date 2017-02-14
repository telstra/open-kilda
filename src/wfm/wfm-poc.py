#!/usr/bin/env python
import logging
import argparse
import threading
import time
import json
from Queue import Queue
from kafka import KafkaConsumer
from kafka import KafkaProducer
from kafka.errors import KafkaError

KAFKA_GROUP_ID = 'kilda-workflow-consumers'
KAFKA_TOPIC = 'kilda-test'
KAFA_TOPICS = [KAFKA_TOPIC]

class IslPoll(threading.Thread):
    daemon = True

    def run(self):
        while True:
            for isl in isls:
                logging.info("checking isl {} - {}".format(isl['switch_id'], isl['port_no']))
                sendIslDiscoverPacket(isl['switch_id'], isl['port_no'])
                time.sleep(1)


class Producer(threading.Thread):
    daemon = True

    def run(self):
        producer = KafkaProducer(bootstrap_servers=args.server)
        producer = KafkaProducer(value_serializer=lambda m: json.dumps(m).encode('ascii'))

        while True:
            message = queue.get()
            logging.info("producer got message: {}".format(message))
            producer.send(KAFKA_TOPIC, message)


class Consumer(threading.Thread):
    daemon = True

    def run(self):
        consumer = KafkaConsumer(bootstrap_servers=args.server,
                                 auto_offset_reset='latest',
                                 group_id=KAFKA_GROUP_ID,
                                 api_version=(0, 9))
        consumer.subscribe(KAFA_TOPICS)

        while True:
            message = consumer.next()
            if message:
                message_obj = json.loads(message.value)
                parseMessage(message_obj)


def parseMessage(message):
    if message['type'] == 'INFO':
        parseInfoMessage(message['data'])


def parseInfoMessage(message):
    if message['message_type'] == 'switch':
        parseSwitchMessage(message)
    elif message['message_type'] == 'port':
        parsePortMessage(message)


def parseSwitchMessage(message):
    state = message['state']
    if state == 'ACTIVATED':
        switchActivated(message)
    elif state == 'ADDED':
        switchAdded(message)
    elif state == 'CHANGED':
        switchChanged(message)
    elif state == 'DEACTIVATED':
        switchDeactivated(message)
    elif state == 'REMOVED':
        switchRemoved(message)


def switchAdded(message):
    logging.info("switch {} added.".format(message['switch_id']))
    switchInsertDefaultFlows(message['switch_id'])

def switchActivated(message):
    logging.info("switch {} activated.".format(message['switch_id']))

def switchDeactivated(message):
    logging.info("switch {} deactivated.".format(message['switch_id']))

def switchRemoved(message):
    logging.info("switch {} removed.".format(message['switch_id']))

def switchChanged(message):
    logging.info("switch {} changed.".format(message['switch_id']))

def parsePortMessage(message):
    state = message['state']
    if state == 'ADD':
        portAdd(message)
    elif state == 'DELETE':
        portDelete(message)
    elif state == 'UP':
        portUp(message)
    elif state == 'DOWN':
        portDown(message)
    elif state == 'OTHER_UPDATE':
        portOther(message)

def portAdd(message):
    logging.info("switch {} port {} added.".format(message['switch_id'],
                 message['port_no']))
    isls.append({'switch_id': message['switch_id'], 'port_no': message['port_no']})

def portDelete(message):
    logging.info("switch {} port {} deleted.".format(message['switch_id'],
                 message['port_no']))

def portUp(message):
    logging.info("switch {} port {} up.".format(message['switch_id'],
                 message['port_no']))
    isls.append({'switch_id': message['switch_id'], 'port_no': message['port_no']})

def portDown(message):
    logging.info("switch {} port {} down.".format(message['switch_id'],
                 message['port_no']))

def portOther(message):
    logging.info("switch {} port {} other change.".format(message['switch_id'],
                 message['port_no']))

def parse_cmdline():
    parser = argparse.ArgumentParser()
    parser.add_argument('server', action='store', help='Kafka server:port.')
    return parser.parse_args()


def switchInsertDefaultFlows(switch_id):
    data = {"destination": "CONTROLLER", "command": "install_default_flows",
            "switch_id": switch_id}
    message = {"type": "COMMAND",
               "timestamp": long(time.time()*1000),
               "data": data}
    logger.info(message)
    queue.put(message)

def sendIslDiscoverPacket(switch_id, port):
    data = {"destination": "CONTROLLER",
            "command": "discover_isl",
            "switch_id": switch_id,
            "port_no": port}
    message = {"type": "COMMAND",
               "timestamp": long(time.time()*1000),
               "data": data}

    logger.info(message)
    queue.put(message)

def main(args):
    global queue
    global isls

    isls = []
    queue = Queue()

    threads = [
        Consumer(),
        Producer(),
        IslPoll()
    ]

    for t in threads:
        t.start()

    while True:
        time.sleep(1)

if __name__ == "__main__":
    logging.basicConfig(
        format='%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d:%(message)s',
        level=logging.INFO
    )
    logger = logging.getLogger(__name__)
    args=parse_cmdline()
    main(args)
