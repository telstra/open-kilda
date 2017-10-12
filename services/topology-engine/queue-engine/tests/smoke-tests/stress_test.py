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
from itertools import izip

bootstrap_servers = 'kafka.pendev:9092'
topic = 'kilda-test'

producer = KafkaProducer(bootstrap_servers=bootstrap_servers)


def generate_swith_name(n):
    i = iter(hex(n)[2:].zfill(16))
    return ':'.join([''.join(a) for a in izip(i, i)])


x = xrange(10000)

for n in x:
    switch = generate_swith_name(n)

    producer.send(topic, b'{"type": "INFO", "timestamp": 23478952134, "destination":"TOPOLOGY_ENGINE", "payload": '
                         b'{"message_type": "switch", '
                         b'"switch_id": "%s",'
                         b' "state": "ADDED", '
                         b'"address":"%s", '
                         b'"hostname":"hostname", '
                         b'"description":"description", '
                         b'"controller":"controller"}}' % (switch, switch))


producer.send(topic, b'{"type": "INFO", "timestamp": 23478952134, "destination":"STOP"}')
producer.flush()
