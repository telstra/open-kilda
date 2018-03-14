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

import sys, copy
from topologylistener import flow_utils, messageclasses, config
from multiprocessing import Pool


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

result = flow_utils.fetch_flow_segments(flow1['flowid'], flow1['cookie'])
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

result = flow_utils.fetch_flow_segments(flow1['flowid'], flow1['cookie'])
print("Validate #4.1 - should have no flow segments: %s " % result)

for i,isl in enumerate(messageclasses.MessageItem.fetch_isls()):
    print("Validate #4.* - available_bandwidth should be %d: is: %d" % ((isls1[i]['available_bandwidth']), isl['available_bandwidth']))

# CREATE INDEX ON :switch(name);
# CREATE INDEX ON :isl(src_switch,src_port,dst_switch,dst_port);
# CREATE INDEX ON :flow_segment(src_switch,src_port,dst_switch,dst_port);

r1a=0x2000000000000001
r1b=0x2000000000000002
f1a=0x4000000000000001
f1b=0x4000000000000002

print("\n\n")
create_flow = {
    u'timestamp': 1519103068803,
    u'correlation_id': 1519103068744,
    u'payload': {u'timestamp': 1519103068797, u'clazz': u'org.openkilda.messaging.info.flow.FlowInfoData',
                 u'flowid': u'c3none-1519103023077', u'correlation_id': u'1519103068744', u'operation': u'CREATE',
                 u'payload': {u'forward': {u'last_updated': u'2018-02-20T05:04:28.796Z', u'description': u'c3none',
                                           u'state': u'ALLOCATED', u'transit_vlan': 2, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:05', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 10000, u'src_switch': u'de:ad:be:ef:00:00:00:03', u'cookie': f1a,
                                           u'dst_port': 2, u'src_vlan': 0, u'dst_vlan': 0, u'src_port': 1,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2, u'segment_latency': 62},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2, u'segment_latency': 13},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1}],
                                               u'latency_ns': 75, u'timestamp': 1519103068795,
                                               u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 1},
                              u'reverse': {u'last_updated': u'2018-02-20T05:04:28.796Z', u'description': u'c3none',
                                           u'state': u'ALLOCATED', u'transit_vlan': 3, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:03', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 10000,u'src_switch': u'de:ad:be:ef:00:00:00:05',u'cookie': r1a,
                                           u'dst_port': 1, u'src_vlan': 0, u'dst_vlan': 0, u'src_port': 2,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1, u'segment_latency': 13},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1, u'segment_latency': 62},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2}],
                                                u'latency_ns': 75, u'timestamp': 1519103068795,
                                                u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 1}
                              }
                 }
}

delete_flow = {
    u'timestamp': 1519103073339,
    u'correlation_id': 1519103073300,
    u'payload': {u'timestamp': 1519103073329, u'clazz': u'org.openkilda.messaging.info.flow.FlowInfoData',
                 u'flowid': u'c3none-1519103023077', u'correlation_id': u'1519103073300', u'operation': u'DELETE',
                 u'payload': {u'forward': {u'last_updated': u'2018-02-20T05:04:28.796Z', u'description': u'c3none',
                                           u'state': u'UP', u'transit_vlan': 2, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:05', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 10000, u'src_switch': u'de:ad:be:ef:00:00:00:03', u'cookie': f1a,
                                           u'dst_port': 2, u'src_vlan': 0, u'dst_vlan': 0,u'src_port': 1,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2, u'segment_latency': 62},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2, u'segment_latency': 13},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1}],
                                               u'latency_ns': 75, u'timestamp': 1519103068795,
                                               u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 1},
                              u'reverse': {u'last_updated': u'2018-02-20T05:04:28.796Z', u'description': u'c3none',
                                           u'state': u'UP', u'transit_vlan': 3, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:03', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 10000, u'src_switch': u'de:ad:be:ef:00:00:00:05', u'cookie': r1a,
                                           u'dst_port': 1, u'src_vlan': 0, u'dst_vlan': 0, u'src_port': 2,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1, u'segment_latency': 13},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1, u'segment_latency': 62},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2}],
                                               u'latency_ns': 75, u'timestamp': 1519103068795,
                                               u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 1
                                           }
                              }
                 }
}

update_flow = {
    u'timestamp': 1519167032591,
    u'correlation_id': 1519167032523,
    u'payload': {u'timestamp': 1519167032580, u'clazz': u'org.openkilda.messaging.info.flow.FlowInfoData',
                 u'flowid': u'c3none-1519103023077', u'correlation_id': u'1519167032523', u'operation': u'UPDATE',
                 u'payload': {u'forward': {u'last_updated': u'2018-02-20T22:50:32.579Z', u'description': u'u3none',
                                           u'state': u'ALLOCATED', u'transit_vlan': 4, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:05', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 20000, u'src_switch': u'de:ad:be:ef:00:00:00:03', u'cookie': f1b,
                                           u'dst_port': 2, u'src_vlan': 0, u'dst_vlan': 0, u'src_port': 1,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2, u'segment_latency': 469},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2, u'segment_latency': 357},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1}],
                                               u'latency_ns': 826, u'timestamp': 1519167032578,
                                               u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 2},
                              u'reverse': {u'last_updated': u'2018-02-20T22:50:32.579Z', u'description': u'u3none',
                                           u'state': u'ALLOCATED', u'transit_vlan': 5, u'ignore_bandwidth': False,
                                           u'dst_switch': u'de:ad:be:ef:00:00:00:03', u'flowid': u'c3none-1519103023077',
                                           u'bandwidth': 20000, u'src_switch': u'de:ad:be:ef:00:00:00:05', u'cookie': r1b,
                                           u'dst_port': 1, u'src_vlan': 0, u'dst_vlan': 0, u'src_port': 2,
                                           u'flowpath': {u'path': [
                                               {u'seq_id': 0, u'switch_id': u'de:ad:be:ef:00:00:00:05', u'port_no': 1, u'segment_latency': 357},
                                               {u'seq_id': 1, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 2},
                                               {u'seq_id': 2, u'switch_id': u'de:ad:be:ef:00:00:00:04', u'port_no': 1, u'segment_latency': 469},
                                               {u'seq_id': 3, u'switch_id': u'de:ad:be:ef:00:00:00:03', u'port_no': 2}],
                                               u'latency_ns': 826, u'timestamp': 1519167032578,
                                               u'clazz': u'org.openkilda.messaging.info.event.PathInfoData'},
                                           u'meter_id': 2}}}

}

print('TEST #5 - create flow through front door (almost .. starting with dict)')
event_create = messageclasses.MessageItem(**create_flow)
event_create.handle()
# TODO: test something here related to create

import logging
logging.basicConfig()
logger = logging.getLogger('topologylistener.messageclasses')

print('TEST #6 - update flow through front door (almost .. starting with dict)')
event_delete = messageclasses.MessageItem(**update_flow)
event_delete.handle()
# TODO: test something here related to delete

print('TEST #7 - delete flow through front door (almost .. starting with dict)')
event_delete = messageclasses.MessageItem(**delete_flow)
event_delete.handle()
# TODO: test something here related to delete


print('TEST #8 - create ... A lot )')
print("==> If this fails, it will not stop, run:  `ps -a | grep test_flow | cut -d ' ' -f 1 | xargs kill` ")
test_range = 20
num_proc = 5

def do_push_alot(iteration):
    if iteration % num_proc == 0:
        sys.stdout.write(" {}".format(iteration))
        sys.stdout.flush()
    cf = copy.deepcopy(create_flow)
    cf[u'payload'][u'operation'] = "PUSH"
    messageclasses.MessageItem(**cf).handle()


p = Pool(processes=num_proc)
p.map(do_push_alot, range(test_range))
print(" DONE ")
print(" ")
