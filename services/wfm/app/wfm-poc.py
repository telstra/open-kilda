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
KAFA_TOPICS = ['kilda-test']
KAFKA_CONSUMER_COUNT = 5
KAFKA_PRODUCER_COUNT = 10
ISL_DISCOVER_FREQUENCY = 30

format = '%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d'
format += ':%(message)s'
logging.basicConfig(
    format=format,
    level=logging.DEBUG
)
logger = logging.getLogger(__name__)


class IslDiscover(threading.Thread):
    daemon = False

    def run(self):
        logging.info("checking isls")
        for isl in isls:
            logging.info("checking isl {} - {}"
                         .format(isl['switch_id'], isl['port_no']))
            sendIslDiscoverPacket(isl['switch_id'], isl['port_no'])
        logging.info("done checking isls")


class IslPoll(threading.Thread):
    daemon = True

    def run(self):
        while not shutting_down:
            if len(isls) > 0:
                IslDiscover().start()
            time.sleep(ISL_DISCOVER_FREQUENCY)


class Producer(threading.Thread):
    daemon = True

    def run(self):
        producer = KafkaProducer(bootstrap_servers=args.server,
                                 value_serializer=lambda
                                 m: json.dumps(m).encode('ascii'))

        while not shutting_down:
            message = queue.get()
            logging.info("sending: {}".format(message))
            producer.send(KAFKA_TOPIC, message)
        producer.close()


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
                    message_obj = json.loads(message.value)
                    parseMessage(message_obj)
            consumer.close()
        except Exception as e:
            logging.error(e)
        finally:
            consumer.close()


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


def switchActivated(message):
    logging.info("switch {} activated.".format(message['switch_id']))
    switchInsertDefaultFlows(message['switch_id'])


def switchDeactivated(message):
    logging.info("switch {} deactivated.".format(message['switch_id']))
    removeSwitchForDiscover(message['switch_id'])


def switchRemoved(message):
    logging.info("switch {} removed.".format(message['switch_id']))
    removeSwitchForDiscover(message['switch_id'])


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
    addPortForDiscover(message['switch_id'], message['port_no'])


def portDelete(message):
    logging.info("switch {} port {} deleted.".format(message['switch_id'],
                 message['port_no']))
    removePortForDiscover(message['switch_id'], message['port_no'])


def portUp(message):
    logging.info("switch {} port {} up.".format(message['switch_id'],
                 message['port_no']))
    addPortForDiscover(message['switch_id'], message['port_no'])


def portDown(message):
    logging.info("switch {} port {} down.".format(message['switch_id'],
                 message['port_no']))
    removePortForDiscover(message['switch_id'], message['port_no'])


def portOther(message):
    logging.info("switch {} port {} other change.".format(message['switch_id'],
                 message['port_no']))


def parse_cmdline():
    parser = argparse.ArgumentParser()
    parser.add_argument('server', action='store',
                        help='Kafka server:port.')
    parser.add_argument('topic', action='store',
                        help='Kafka topic to listen to')
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


def addPortForDiscover(switch_id, port):
    is_present = False
    for isl in isls:
        if isl['switch_id'] == switch_id and isl['port_no'] == port:
            is_present = True
    if not is_present:
        isls.append({'switch_id': switch_id, 'port_no': port})


def removePortForDiscover(switch_id, port):
    logger.debug("checking if isl discovery is enabled on {} - {}"
                 .format(switch_id, port))
    for isl in isls:
        if isl['switch_id'] == switch_id and isl['port_no'] == port:
            logger.info("removing {} port {} from discovery"
                        .format(switch_id, port))
            isls.remove(isl)      # TODO: how to deal with messages in Kafka


def removeSwitchForDiscover(switch_id):
    logger.debug("checking if any isl discovers are on associated with {}"
                 .format(switch_id))
    for isl in isls:
        if isl['switch_id'] == switch_id:
            logger.info("removing {} from discovery".format(isl))
            isls.remove(isl)      # TODO: how to deal with messages in Kafka


def main(args):
    global queue
    global isls
    global shutting_down
    shutting_down = False

    isls = []
    queue = Queue()

    threads = [
        IslPoll()
    ]

    for i in Range(KAFKA_CONSUMER_COUNT):
        threads.append(Consumer())

    for i in Range(KAFKA_PRODUCER_COUNT):
        threads.append(Producer())

    for t in threads:
        t.start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt as e:
        logger.info("shutting down")
        shutting_down = True

if __name__ == "__main__":
    args = parse_cmdline()
    logging.info("bootstrap_servers = {}".format(args.server))
    main(args)
