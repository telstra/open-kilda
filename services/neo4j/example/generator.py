#!/usr/bin/python
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

