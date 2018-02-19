#
# (1) Run this from the root directory, where topology_engine.properties lives (there is some code dependency on that.
# For example, run from $KILDA_HOME/services/topology-engine/queue-engine
#
# (2) Primary purpose of this file is to do manual testing of underlying functionality.
#
# Types of tests:
#   1. flow creation
#   2. get segments (validate segments were created
#   3. add isl .. validate bandwidth deducted.
#
# Example command line start:
#   `PYTHONPATH= python tests/test_flow_segments.py`
#

import time
from topologylistener import flow_utils, messageclasses, config


flow1 = {
    'flowid' : 'fred_id',
    'cookie': 1000,
    'meter_id': 2000,
    'bandwidth': 3000,
    'ignore_bandwidth': False,
    'src_port': 1,
    'dst_port': 2,
    'src_switch': 'DE:AD:BE:EF:11:11:11:11',
    'dst_switch': 'DE:AD:BE:EF:33:33:33:33',
    'src_vlan': 3,
    'dst_vlan': 4,
    'transit_vlan': 5,
    'description': 'Test Flow #1',
    'last_updated': 'Last Full Moon',
    'flowpath': {
        'latency_ns': 10,
        'path': [
            {'switch_id': 'DE:AD:BE:EF:11:11:11:11', 'port_no': 5, 'seq_id': 1, 'segment_latency': 11},
            {'switch_id': 'DE:AD:BE:EF:22:22:22:22', 'port_no': 6, 'seq_id': 2, 'segment_latency': 22},
            {'switch_id': 'DE:AD:BE:EF:22:22:22:22', 'port_no': 7, 'seq_id': 3, 'segment_latency': 33},
            {'switch_id': 'DE:AD:BE:EF:33:33:33:33', 'port_no': 8, 'seq_id': 4, 'segment_latency': 44},
        ]
    }
}

isls1 = [
    {
        'latency_ns': 100,
        'speed': 1000,
        'available_bandwidth': 10000,
        'path': [{'switch_id': 'DE:AD:BE:EF:11:11:11:11','port_no': 5},
                 {'switch_id': 'DE:AD:BE:EF:22:22:22:22','port_no': 6}]
    },
    {
        'latency_ns': 200,
        'speed': 2000,
        'available_bandwidth': 20000,
        'path': [{'switch_id': 'DE:AD:BE:EF:22:22:22:22', 'port_no': 7},
                 {'switch_id': 'DE:AD:BE:EF:33:33:33:33', 'port_no': 8}]
    }]

print('Hello World')

print("TEST #1 & #2 - create flow, get segments, spot check some of the values.")
flow_utils.store_flow(flow1)

result = flow_utils.fetch_flow_segments(flow1)
print("Validate #1 - Length of segments should be 2: %d" % len(result))
for i,val in enumerate(result):
    print("Validate #3.* - seq_id should be %d: is: %d" % (flow1['flowpath']['path'][i*2]['seq_id'], val['seq_id']))

print("TEST #3 - create isls, validate bandwidth.")
for isl in isls1:
    mc = messageclasses.MessageItem(payload=isl)
    mc.create_isl()

for i,isl in enumerate(messageclasses.MessageItem.fetch_isls()):
    print("Validate #3.* - available_bandwidth should be %d: is: %d" % ((-3000+isls1[i]['available_bandwidth']), isl['available_bandwidth']))

print("TEST #4 - remove flow, validate bandwidth.")
flow_utils.remove_flow(flow1)

result = flow_utils.fetch_flow_segments(flow1)
print("Validate #4.1 - should have no flow segments: %s " % result)

for i,isl in enumerate(messageclasses.MessageItem.fetch_isls()):
    print("Validate #4.* - available_bandwidth should be %d: is: %d" % ((isls1[i]['available_bandwidth']), isl['available_bandwidth']))

# CREATE INDEX ON :switch(name);
# CREATE INDEX ON :isl(src_switch,src_port,dst_switch,dst_port);
# CREATE INDEX ON :flow_segment(src_switch,src_port,dst_switch,dst_port);
