from unittest import TestCase

from cli.novi_cli import NoviCli

class TestStringMethods(TestCase):

    def test_parse_dump_flows(self):
        test_data = '''        [##################################################] 100%       Flow entries
[FLOW_ENTRIES] Total entries: 2
[TABLE 0] Total entries: 2
    [FLOW_ID0]
        Timestamp        = Tue Jun 20 07:28:39 2023
        TableId          = 0
        ofp_version      = 6
        ControllerGroup  = cli
        ControllerId     = cli
        Priority         = 1000
        Idle_timeout     = 0
        Hard_timeout     = 0
        Importance       = 0
        Packet_count     = 19365
        Byte_count       = 5180300
        Cookie           = 0
        Send_flow_rem    = false
        Persistent       = false
        [MATCHFIELDS]
            OFPXMT_OFB_IN_PORT = 1
        [INSTRUCTIONS]
            [OFPIT_APPLY_ACTIONS]
                 [ACTIONS]
                    [OFPAT_OUTPUT]
                        port = 8
    [FLOW_ID1]
        Timestamp        = Tue Jun 20 07:29:01 2023
        TableId          = 0
        ofp_version      = 6
        ControllerGroup  = cli
        ControllerId     = cli
        Priority         = 1000
        Idle_timeout     = 0
        Hard_timeout     = 0
        Importance       = 0
        Packet_count     = 19386
        Byte_count       = 5185892
        Cookie           = 0
        Send_flow_rem    = false
        Persistent       = false
        [MATCHFIELDS]
            OFPXMT_OFB_IN_PORT = 8
        [INSTRUCTIONS]
            [OFPIT_APPLY_ACTIONS]
                 [ACTIONS]
                    [OFPAT_OUTPUT]
                        port = 1

[TABLE 1] Total entries: 0

[TABLE 2] Total entries: 0

[TABLE 3] Total entries: 0

[TABLE 4] Total entries: 0

[TABLE 5] Total entries: 0

[TABLE 6] Total entries: 0
'''

        self.assertEqual([{
            'in_port': 1,
            'out_port': 8
        }, {
            'in_port': 8,
            'out_port': 1
        }], NoviCli._parse_dump_flows(test_data))
