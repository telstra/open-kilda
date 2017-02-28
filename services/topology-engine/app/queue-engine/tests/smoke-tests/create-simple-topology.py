#!/usr/bin/python
from kafka import KafkaConsumer, KafkaProducer
import time
from random import randint
import time

bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'

producer = KafkaProducer(bootstrap_servers=bootstrapServer)

loopSize = 4

for n in range(1, loopSize):
    switch_id = n
    linked_id = switch_id + 1
    if linked_id == loopSize:
        linked_id = 1
    
    switch_id = str(switch_id).zfill(2)
    linked_id = str(linked_id).zfill(2)

    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952134, "data": {{"message_type": "switch", "switch_id": "00:00:00:00:00:00:00:{}", "state": "ADDED"}}}}'.format(switch_id))
    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "data": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 1, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 2, "seq_id": "1"}}]}}}}'.format(switch_id, linked_id))

    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "data": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 1, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 2, "seq_id": "1"}}]}}}}'.format(linked_id, switch_id))



'''
start_time = time.time()
consumer = KafkaConsumer(bootstrap_servers=bootstrapServer, auto_offset_reset='earliest')
consumer.subscribe([topic])

count = 0


while consumer.next():
    print("--- %s seconds ---" % (time.time() - start_time))

'''