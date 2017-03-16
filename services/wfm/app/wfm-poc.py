#!/usr/bin/env python
import logging
import argparse
import threading
import time
import json
from Queue import Queue
from kafka import KafkaConsumer
from kafka import KafkaProducer

KAFKA_GROUP_ID = 'kilda-workflow-consumers'
KAFKA_TPE_TOPIC = 'kilda-test'
KAFKA_SPK_TOPIC = 'kilda-speaker'
KAFKA_TOPICS = [KAFKA_TPE_TOPIC, KAFKA_SPK_TOPIC]
KAFKA_CONSUMER_COUNT = 5
KAFKA_PRODUCER_COUNT = 10
ISL_DISCOVER_FREQUENCY = 30

queue = Queue()
isls = []
shutting_down = False

format = '%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d'
format += ':%(message)s'
logging.basicConfig(
    format=format,
    level=logging.INFO
)
logger = logging.getLogger(__name__)


class IslDiscover(threading.Thread):
    """
    Discovers ISL's in topology
    """
    daemon = False

    def run(self):
        """
        Send isl_discover_packet for each active ISL
        """
        logging.info("checking isls")
        for isl in isls:
            logging.info("checking isl {} - {}"
                         .format(isl['switch_id'], isl['port_no']))
            send_isl_discover_packet(isl['switch_id'], isl['port_no'])
        logging.info("done checking isls")


class IslPoll(threading.Thread):
    """
    Poll for ISL status
    """
    daemon = True

    def run(self):
        """
        Sleeps for ISL_DISOVER_FREQUENCY then discovers/monitors ISLs
        """
        while not shutting_down:
            if len(isls) > 0:
                IslDiscover().start()
            time.sleep(ISL_DISCOVER_FREQUENCY)


class Producer(threading.Thread):
    """
    Kafka Producer
    """
    daemon = True

    def run(self):
        """
        Drains queue and sends messages to Kafka
        """
        producer = KafkaProducer(bootstrap_servers=args.server,
                                 value_serializer=lambda
                                 m: json.dumps(m).encode('ascii'))

        while not shutting_down:
            message = queue.get()
            logging.info("sending: {}".format(message))
            producer.send(message['topic'], message['message'])
            message.done()
        producer.close()


class Consumer(threading.Thread):
    """
    Kafka Consumer
    """
    daemon = True

    def run(self):
        """
        Listens to Kafka and grabs messages
        """
        consumer = KafkaConsumer(bootstrap_servers=args.server,
                                 auto_offset_reset='latest',
                                 group_id=KAFKA_GROUP_ID,
                                 api_version=(0, 10))
        consumer.subscribe(KAFKA_TOPICS)

        try:
            while not shutting_down:
                message = consumer.next()
                if message:
                    logging.info("recevied: {}".format(message))
                    message_obj = json.loads(message.value)
                    parse_message(message_obj)
            consumer.close()
        except Exception as e:
            logging.error(e)
        finally:
            consumer.close()


def parse_message(message):
    """
    Parse Kafka Messages

    Args:
        message: kafka message
    """
    if message['type'] == 'INFO':
        parse_info_message(message['data'])


def parse_info_message(message):
    """
    Parse INFO messages

    Args:
        message: info message
    """
    if message['message_type'] == 'switch':
        parse_switch_message(message)
    elif message['message_type'] == 'port':
        parse_port_message(message)


def parse_switch_message(message):
    """
    Parse switch messages

    Args:
        message: switch message
    """
    state = message['state']
    if state == 'ACTIVATED':
        switch_activated(message)
    elif state == 'ADDED':
        switch_added(message)
    elif state == 'CHANGED':
        switch_changed(message)
    elif state == 'DEACTIVATED':
        switch_deactivated(message)
    elif state == 'REMOVED':
        switch_removed(message)


def switch_added(message):
    """
    Switch added; currently don't do anyting but log as we only care about ACTIVATED

    Args:
        message: switch message
    """
    logging.info("switch {} added.".format(message['switch_id']))


def switch_activated(message):
    """
    New switch activated

    Args:
        message: switch message
    """
    logging.info("switch {} activated.".format(message['switch_id']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    switch_insert_default_flows(message['switch_id'])


def switch_deactivated(message):
    """
    Switch deactivated; remove from ISL polling.

    Args:
        message: switch message
    """
    logging.info("switch {} deactivated.".format(message['switch_id']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    remove_switch_for_discover(message['switch_id'])


def switch_removed(message):
    """
    Switch removed; remove from ISL polling

    Args:
        message: switch message
    """
    logging.info("switch {} removed.".format(message['switch_id']))
    remove_switch_for_discover(message['switch_id'])


def switch_changed(message):
    """
    Switch changed; currently don't do anything.

    Args:
        message: switch message
    """
    logging.info("switch {} changed.".format(message['switch_id']))


def parse_port_message(message):
    """
    Parse a port message.

    Args:
        message: port message
    """
    state = message['state']
    if state == 'ADD':
        port_add(message)
    elif state == 'DELETE':
        port_delete(message)
    elif state == 'UP':
        port_up(message)
    elif state == 'DOWN':
        port_down(message)
    elif state == 'OTHER_UPDATE':
        port_other(message)


def port_add(message):
    """
    Port added; start ISL discovery

    Args:
        message: port message
    """
    logging.info("switch {} port {} added.".format(message['switch_id'],
                 message['port_no']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    add_port_for_discover(message['switch_id'], message['port_no'])


def port_delete(message):
    """
    Port deleted; stop ISL discovery

    Args:
        message: port message
    """
    logging.info("switch {} port {} deleted.".format(message['switch_id'],
                 message['port_no']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    remove_port_for_discover(message['switch_id'], message['port_no'])


def port_up(message):
    """
    Port up; start ISL discovery on the port.

    Args:
        message: port message
    """
    logging.info("switch {} port {} up.".format(message['switch_id'],
                 message['port_no']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    add_port_for_discover(message['switch_id'], message['port_no'])


def port_down(message):
    """
    Port down; stop ISL discovery

    Args:
        message: port message
    """
    logging.info("switch {} port {} down.".format(message['switch_id'],
                 message['port_no']))
    queue.put({"topic": KAFKA_TPE_TOPIC, "message": message})
    remove_port_for_discover(message['switch_id'], message['port_no'])


def port_other(message):
    """
    Port other type received; log and ignore.

    Args:
        message: port message
    """
    logging.info("switch {} port {} other change.".format(message['switch_id'],
                 message['port_no']))


def parse_cmdline():
    """
    Parse commandline.

    Returns: args[]

    """
    parser = argparse.ArgumentParser()
    parser.add_argument('server', action='store',
                        help='Kafka server:port.')
    return parser.parse_args()


def switch_insert_default_flows(switch_id):
    """
    Send command to insert default flows

    Args:
        switch_id: datapathID of the switch
    """
    data = {"destination": "CONTROLLER", "command": "install_default_flows",
            "switch_id": switch_id}
    message = {"type": "COMMAND",
               "timestamp": long(time.time()*1000),
               "data": data}
    logger.info(message)
    queue.put({"topic": KAFKA_SPK_TOPIC, "message": message})


def send_isl_discover_packet(switch_id, port):
    """
    Send command to test switch port for ISL

    Args:
        switch_id: datapathID of switch
        port: port number as int
    """
    data = {"destination": "CONTROLLER",
            "command": "discover_isl",
            "switch_id": switch_id,
            "port_no": port}
    message = {"type": "COMMAND",
               "timestamp": long(time.time()*1000),
               "data": data}

    logger.info(message)
    queue.put({"topic": KAFKA_SPK_TOPIC, "message": message})


def add_port_for_discover(switch_id, port):
    """
    Add switch port to list of switches to check.

    Args:
        switch_id: datapathID of switch
        port: port number as int
    """
    is_present = False
    for isl in isls:
        if isl['switch_id'] == switch_id and isl['port_no'] == port:
            is_present = True
    if not is_present:
        isls.append({'switch_id': switch_id, 'port_no': port})


def remove_port_for_discover(switch_id, port):
    """
    Remove port from list of switches to check.

    Args:
        switch_id: datapathID of switch
        port: port number as int
    """
    logger.debug("checking if isl discovery is enabled on {} - {}"
                 .format(switch_id, port))
    for isl in isls:
        if isl['switch_id'] == switch_id and isl['port_no'] == port:
            logger.info("removing {} port {} from discovery"
                        .format(switch_id, port))
            isls.remove(isl)      # TODO: how to deal with messages in Kafka


def remove_switch_for_discover(switch_id):
    """
    Remove all ISL's that have this switchID

    Args:
        switch_id: datapathID of switch
    """
    logger.debug("checking if any isl discovers are on associated with {}"
                 .format(switch_id))
    for isl in isls:
        if isl['switch_id'] == switch_id:
            logger.info("removing {} from discovery".format(isl))
            isls.remove(isl)      # TODO: how to deal with messages in Kafka


def main():
    """
    Main event loop
    """
    shutting_down = False

    threads = [
        IslPoll(),
        Consumer(),
        Producer()
    ]

    for t in threads:
        t.start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logger.info("shutting down")
        shutting_down = True

if __name__ == "__main__":
    args = parse_cmdline()
    logging.info("bootstrap_servers = {}".format(args.server))
    main()
