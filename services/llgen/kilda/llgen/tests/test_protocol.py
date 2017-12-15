#
from kilda.llgen import protocol


class TestProtocol(object):
    def test_pack_unpack(self):
        raw = {
            'module': 'scapy',
            'items': [
                {'kind': 'producer',  # same for 'kind' == 'consumer'
                 'name': 'qwe',  # only for address in list/stats
                 'iface': 'lo',
                 'payload': 'bytes',
                 'ip4': {'dest': '127.0.0.1'},
                 'vlan': 123,
                 'ether': {
                     'source': '00:11:22:33:44:55',
                     'dest': '66:77:88:99:aa:bb'}},
                {'kind': 'drop',
                 'filter': '*globs*'},
                {'kind': 'list'},
                {'kind': 'stats'},
                {'kind': 'time-to-live', 'stop_in_seconds': 60}
            ]
        }

        instance = protocol.InputMessage.unpack(raw)
        revert = instance.pack()

        expected = {
            'module': 'scapy',
            'time_to_live': None,
            'items': [{
                'kind': 'producer',
                'name': 'qwe',
                'iface': 'lo',
                'payload': 'bytes',
                'vlan': 123,
                'ethernet': {
                    'source': {
                        'address': '00:11:22:33:44:55',
                        'is_defined': True
                    },
                    'dest': {
                        'address': '66:77:88:99:aa:bb',
                        'is_defined': True
                    }
                },
                'ip4': {
                    'source': {
                        'address': None,
                        'is_defined': False
                    },
                    'dest': {
                        'address': '127.0.0.1',
                        'is_defined': True
                    }
                }
            }, {
                'kind': 'drop', 'filter': '*globs*'
            }, {
                'kind': 'list'
            }, {
                'kind': 'stats'
            }, {
                'kind': 'time-to-live', 'stop_in_seconds': 60
            }]
        }

        assert(revert == expected)
