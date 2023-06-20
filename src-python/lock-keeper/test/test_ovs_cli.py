from unittest import TestCase

from cli.ovs_cli import OvsCli


class TestStringMethods(TestCase):

    def test_parse_dump_flows(self):
        test_data = '''OFPST_FLOW reply (OF1.3) (xid=0x2):
                cookie=0x0, duration=782.127s, table=0, n_packets=0, n_bytes=0, in_port=7 actions=output:8
                cookie=0x0, duration=782.127s, table=0, n_packets=0, n_bytes=0, in_port=51 actions=output:52'''

        self.assertEqual([{
            'in_port': 7,
            'out_port': 8
        }, {
            'in_port': 51,
            'out_port': 52
        }], OvsCli._parse_dump_flows(test_data))
