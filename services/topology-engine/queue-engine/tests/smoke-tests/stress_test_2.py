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

from kafka import KafkaProducer

bootstrap_servers = 'kafka.pendev:9092'
topic = 'kilda-test'

producer = KafkaProducer(bootstrap_servers=bootstrap_servers)


producer.send(topic, b'{"type": "INFO", "timestamp": 23478952134, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "switch", "switch_id": "00:00:00:00:00:00:00:00", "state": "ADDED", "address":"00:00:00:00:00:00:00:00", "hostname":"hostname", "description":"description", "controller":"controller"}}')
producer.send(topic, b'{"type": "INFO", "timestamp": 23478952134, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "switch", "switch_id": "00:00:00:00:00:00:00:01", "state": "ADDED", "address":"00:00:00:00:00:00:00:01", "hostname":"hostname", "description":"description", "controller":"controller"}}')

producer.flush()

i = 2

while i < 10000:
    producer.send(topic, b'{"type": "INFO", "timestamp": 23478952136, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "isl",  "state": "DISCOVERED", "latency_ns": 1123, "speed":1000000, "available_bandwidth":1000000, "path": [{"switch_id": "00:00:00:00:00:00:00:00", "port_no": 1, "seq_id": "0", "segment_latency": 1123}, {"switch_id": "00:00:00:00:00:00:00:01", "port_no": 1, "seq_id": "1"}]}}')
    i += 1
    producer.send(topic, b'{"type": "INFO", "timestamp": 23478952136, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "isl", "state": "DISCOVERED", "latency_ns": 1123, "speed":1000000, "available_bandwidth":1000000, "path": [{"switch_id": "00:00:00:00:00:00:00:01", "port_no": 1, "seq_id": "0", "segment_latency": 1123}, {"switch_id": "00:00:00:00:00:00:00:00", "port_no": 1, "seq_id": "1"}]}}')
    i += 1
    producer.send(topic, b'{"type": "INFO", "timestamp": 23478952136, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "isl", "state": "FAILED", "path": [{"switch_id": "00:00:00:00:00:00:00:00", "port_no": 1}]}}')
    i += 1
    producer.send(topic, b'{"type": "INFO", "timestamp": 23478952136, "destination":"TOPOLOGY_ENGINE", "payload": {"message_type": "isl", "state": "FAILED", "path": [{"switch_id": "00:00:00:00:00:00:00:01", "port_no": 1}]}}')
    i += 1
    print i

producer.flush()
