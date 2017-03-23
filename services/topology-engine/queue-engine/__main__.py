#!/usr/bin/python
print "Topology engine starting."
import topologylistener
import threading

threadcount = 0

try:
    print "Starting lister thread."
    threadcount += 1
    t1 = threading.Thread(target=topologylistener.eventhandler.get_events,args=(threadcount,))
    t1.daemon =True
    t1.start()

    threadcount += 1
    t2 = threading.Thread(target=topologylistener.eventhandler.get_events,args=(threadcount,))
    t2.daemon =True
    t2.start()    
    
    t1.join()
    t2.join()

    #topologylistener.eventhandler.get_events()
except Exception as e:
    print "Listener thread unhandled exception:"
    print e
