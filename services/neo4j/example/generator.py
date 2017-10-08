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

from random import randint

count = 1
while 1000 > count:
	print('CREATE (Location'+str(count)+':Location { name: "Location '+ str(count) +'" })')
	count += 1

count = 1
print('create')
while 1000 > count:
	rnd = count
	while rnd == count:
		rnd = (randint(1,999))

        print('(Location'+str(count)+')-[:CONNECTED_TO { distance: 1 }]->(Location'+str(rnd)+'),')
	print('(Location'+str(rnd)+')-[:CONNECTED_TO { distance: 1 }]->(Location'+str(count)+'),')
        count += 1

count = 1
while 999 > count:
        rnd = count + 1

        print('(Location'+str(count)+')-[:CONNECTED_TO { distance: 1 }]->(Location'+str(rnd)+'),')
        print('(Location'+str(rnd)+')-[:CONNECTED_TO { distance: 1 }]->(Location'+str(count)+'),')
        count += 1

