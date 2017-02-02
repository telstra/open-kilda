from kafka import KafkaConsumer, KafkaProducer
import json
import time

print "Connecting to kafka"

bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'
kafkaConnectionRetries = 10

'''
producer = KafkaProducer(bootstrap_servers=bootstrapServer)
producer.send(topic, b'{"message-type": "SWITCH","timestamp": 1485994060989,"controller": "1","data": {"event-type": "ADDED","switch-id": "00:00:00:00:00:00:00:01"}}')
producer.send(topic, b'{"message-type": "SWITCH","timestamp": 1485994060989,"controller": "1","data": {"event-type": "ADDED","switch-id": "00:00:00:00:00:00:00:02"}}')
producer.send(topic, b'{"message-type": "PATH","timestamp": 1485994078693,"controller": "1","data": {"id": 0,"type": "ISL","links": [{"latency": 19,"nodes": [{"switch": "00:00:00:00:00:00:00:02","port": 1},{"switch": "00:00:00:00:00:00:00:01","port": 1}]}]}}')
'''

while kafkaConnectionRetries > 0:
    try:
        time.sleep(1)
        kafkaConnectionRetries -= 1
        consumer = KafkaConsumer(bootstrap_servers=bootstrapServer, auto_offset_reset='earliest')
        consumer.subscribe([topic])
        print "Connected to kafka"
        break
    except:
        print "Waiting for kafka topic {} to become available".format(topic)
        time.sleep(1)

def readMessage():
    message = consumer.next()
    return message.value