#!/usr/bin/python
from clean_topology import cleanup
from create_topology import create_topo


print "\n -- "
cleanup()
create_topo('multi-path-topology.json')
print "\n -- "
