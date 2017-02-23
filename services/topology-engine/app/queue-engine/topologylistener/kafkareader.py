from kafka import KafkaConsumer
import json
import time

print "Connecting to kafka using application defined configuration:"

bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'
kafkaConnectionRetries = 100

print "Server: {}".format(bootstrapServer)
print "Kafka topic: {}".format(topic)
print "Retry limit: {}".format(kafkaConnectionRetries)


while kafkaConnectionRetries > 0:
    try:
        print "Creating Kafka consumer for '{}' at '{}'".format(topic, bootstrapServer)
        kafkaConnectionRetries -= 1
        consumer = KafkaConsumer(bootstrap_servers=bootstrapServer, auto_offset_reset='earliest')
        consumer.subscribe([topic])
        print "Connected to kafka"
        break
    except Exception as e:
        print "The follow error was generated:"
        print e
        print "{} retries remaining.".format(kafkaConnectionRetries-1)
        time.sleep(5)

def readMessage():
    try:
        message = consumer.next()
        if message:
            print "Message retrieved from topic '{}'".format(topic)
            return message.value
    except Exception as e:
        print "Failed to retrive message from topic '{}'".format(topic)
        print e
    