# Copyright 2018 Telstra Open Source
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

from service.lockkeeper import parse_dump_flows


def test_parse_dump_flows():
    test_data = '''OFPST_FLOW reply (OF1.3) (xid=0x2):
         cookie=0x0, duration=782.127s, table=0, n_packets=0, n_bytes=0, in_port=7 actions=output:8
         cookie=0x0, duration=782.127s, table=0, n_packets=0, n_bytes=0, in_port=51 actions=output:52
         cookie=0x8000000000000001, duration=3.256s, table=0, n_packets=0, n_bytes=0, priority=1 actions=drop
         cookie=0x0, duration=782.132s, table=0, n_packets=0, n_bytes=0, priority=0 actions=NORMAL
        '''

    assert parse_dump_flows(test_data) == [
        {
            'in_port': 7,
            'out_port': 8
        }, {
            'in_port': 51,
            'out_port': 52
        }
    ]
