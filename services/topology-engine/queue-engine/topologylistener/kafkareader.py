from kafka import KafkaConsumer, TopicPartition
import json
import time
import os
print "Connecting to kafka using application defined configuration:"

def create_consumer():
    
    bootstrapServer = os.environ['bootstrapserver']
    topic = os.environ['topic']
    group = os.environ['group']

    while True:
        try: 
            consumer = KafkaConsumer(bootstrap_servers=bootstrapServer, group_id=group, auto_offset_reset='earliest')
            consumer.subscribe(['{}'.format(topic)])
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
        if message.value is not "":
            return message.value
        else:
            print "sleeping"
            time.sleep(1)
    except Exception as e:
        print e
