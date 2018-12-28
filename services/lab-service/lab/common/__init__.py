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

import sys
import signal
import time
import json
from multiprocessing import Process
from threading import Thread
from logging.config import dictConfig


def init_logger():
    with open("./log.json", "r") as fd:
        dictConfig(json.load(fd))


def run_process(run_fn):
    proc = Process(target=run_fn)
    proc.start()
    return proc


def run_thread(run_fn):
    thread = Thread(target=run_fn)
    thread.start()
    return thread


def loop_forever(teardown_fn):
    def shutdown(_signo, _stack_frame):
        teardown_fn()
        sys.exit(0)
    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    while True:
        time.sleep(1)
