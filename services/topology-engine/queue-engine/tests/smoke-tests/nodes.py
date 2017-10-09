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

import json
import requests

class Nodes(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Edge(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Link(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)




data = {'query' : 'MATCH (n) return n'}
auth = ('neo4j', 'temppass')
result_switches = requests.post('http://localhost:7474/db/data/cypher', data=data, auth=auth)


j_switches = json.loads(result_switches.text)
nodes = Nodes()
nodes.edges = []

for n in j_switches['data']:
    for r in n:
        result_relationships = requests.get(str(r['outgoing_relationships']), auth=auth)
        j_paths = json.loads(result_relationships.text)
        outgoing_relationships = []
        for j_path in j_paths:
            target = Link()
            if j_path['type'] == u'isl':
                edge = Edge()
                source = Link()
                source.label = r['data']['name']
                source.id = r['metadata']['id']

                dest_node = requests.get(str(j_path['end']), auth=auth)
                j_dest_node = json.loads(dest_node.text)

                target.label = j_path['data']['dst_switch']
                target.id = j_dest_node['metadata']['id']
                edge.value = "{} to {}".format(source.label, target.label)
                edge.source = source
                edge.target = target
                nodes.edges.append(edge)
print nodes.toJSON()

'''
nodes = Nodes()
nodes.edges = []

i = 0

while i < 10:
    edge = Edge()
    source = Link()
    target = Link()

    source.id = i
    source.label = "s{}".format(str(i))

    target.id = i + 1
    target.label = "s{}".format(str(i + 1))

    edge.value = "link: {}".format(str(i))
    edge.source = source
    edge.target = target


    nodes.edges.append(edge)

    i += 1

#print nodes.toJSON()

'''
