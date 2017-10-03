#!/usr/bin/python
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
