#!/usr/bin/python
# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

from kafka import KafkaConsumer, KafkaProducer
import time, json, time, requests
from random import randint

bootstrapServer = 'kafka.pendev:9092'
topic = 'kilda-test'

producer = KafkaProducer(bootstrap_servers=bootstrapServer)

loopSize = 3

loopSize += 1
topology = []

for n in range(1, loopSize):
    node = {}
    if loopSize > 99:
        loopSize = 99

    switch_id = n
    linked_id_next = switch_id + 1
    linked_id_prev = switch_id - 1

    if linked_id_next == loopSize:
        linked_id_next = 1

    if linked_id_prev < 1:
        linked_id_prev = loopSize - 1

    switch_id = str(switch_id).zfill(2)
    linked_id_next = str(linked_id_next).zfill(2)
    linked_id_prev = str(linked_id_prev).zfill(2)

    node = {}
    node['name'] = "00:00:00:00:00:00:00:{}".format(switch_id)
    outgoing_relationships = []
    outgoing_relationships.append("00:00:00:00:00:00:00:{}".format(linked_id_prev))
    outgoing_relationships.append("00:00:00:00:00:00:00:{}".format(linked_id_next))
    outgoing_relationships.sort()
    node['outgoing_relationships'] = outgoing_relationships
    topology.append(node)

    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952134, "payload": {{"message_type": "switch", "switch_id": "00:00:00:00:00:00:00:{}", "state": "ADDED"}}}}'.format(switch_id))
    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "payload": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 1, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 2, "seq_id": "1"}}]}}}}'.format(switch_id, linked_id_next))
    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "payload": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 2, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 1, "seq_id": "1"}}]}}}}'.format(switch_id, linked_id_prev))

    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "payload": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 3, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 4, "seq_id": "1"}}]}}}}'.format(switch_id, linked_id_next))
    producer.send(topic, b'{{"type": "INFO", "timestamp": 23478952136, "payload": {{"message_type": "isl", "latency_ns": 1123, "path": [{{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 4, "seq_id": "0", "segment_latency": 1123}}, {{"switch_id": "00:00:00:00:00:00:00:{}", "port_no": 3, "seq_id": "1"}}]}}}}'.format(switch_id, linked_id_prev))


headers = {'Content-Type': 'application/json'}
time.sleep(5)
result_recv = requests.get('http://localhost', headers=headers)

recv_topo = result_recv.text
sent_topo = json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True)

if recv_topo == sent_topo:
    print "Topology created and validated"
else:
    print "Error in test please check."
    print "Sent topo:"
    print sent_topo
    print "Recv topo"
    print recv_topo
