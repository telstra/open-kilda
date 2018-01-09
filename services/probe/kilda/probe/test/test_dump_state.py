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

import glob
from unittest.mock import MagicMock

from kilda.probe.command.dump_state import print_table


def test_basic_smoke_dump_state():
    """
    Load all res/*BoltState.json and try print them
    """

    states = []

    for filename in glob.glob('./kilda/probe/test/res/*BoltState.json'):
        with open(filename) as f:
            m = MagicMock()
            m.value = f.read()
            states.append(m)

    print_table(states, True)
