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

import traceback
import threading

print "Topology engine starting."

import topologylistener

thread_count = 0

try:
    print "Starting lister thread."

    thread_count += 1
    t1 = threading.Thread(target=topologylistener.eventhandler.get_events,
                          name="te-{}".format(thread_count),
                          args=(thread_count,))

    t1.daemon = True
    t1.start()

    thread_count += 1
    t2 = threading.Thread(target=topologylistener.eventhandler.get_events,
                          name="te-{}".format(thread_count),
                          args=(thread_count,))

    t2.daemon = True
    t2.start()

    t1.join()
    t2.join()

    # topologylistener.eventhandler.get_events()

except Exception as e:
    print "Listener thread unhandled exception: {}".format(e.message)
    traceback.print_exc()
