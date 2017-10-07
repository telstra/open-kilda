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

import requests
import json
from time import time


def cleanup():
    print "\nClearing existing topology."
    start = time()
    headers = {'Content-Type': 'application/json'}
    result = requests.post('http://localhost:38080/cleanup', headers=headers)
    print "==> Time: ", time()-start
    print "==> Successful", result


if __name__ == "__main__":
    cleanup()

