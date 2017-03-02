from kafka import KafkaConsumer
import json
import time
import os
print "Connecting to kafka using application defined configuration:"

def create_consumer():
    
    bootstrapServer = os.environ['bootstrapServer']
    topic = os.environ['topic']

    while True:
        try: 
            consumer = KafkaConsumer(bootstrap_servers=bootstrapServer, auto_offset_reset='earliest')
            consumer.subscribe([topic])
            print "Connected to kafka"
            break
        except Exception as e:
            print "The follow error was generated:"
            print e
            time.sleep(5)
    return consumer

def read_message(consumer):
    try:
        message = consumer.next()
        if message:
            return message.value
        else:
            time.sleep(1)
    except Exception as e:
        print e
