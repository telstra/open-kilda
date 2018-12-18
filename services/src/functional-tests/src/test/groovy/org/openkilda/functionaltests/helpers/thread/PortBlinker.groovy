package org.openkilda.functionaltests.helpers.thread

import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService

class PortBlinker extends Thread {
    private NorthboundService northbound
    private volatile boolean running

    final Switch sw
    final int port
    Date timeStarted
    Date timeStopped

    PortBlinker(NorthboundService northbound, Switch sw, int port) {
        super()
        this.northbound = northbound
        this.sw = sw
        this.port = port
    }

    @Override
    synchronized void start() {
        super.start()
        timeStarted = new Date()
    }

    @Override
    void run() {
        running = true
        while(running) {
            northbound.portDown(sw.dpId, port)
            northbound.portUp(sw.dpId, port)
        }
    }

    void stop(boolean endWithPortUp) {
        running = false
        join()
        if(!endWithPortUp) {
            northbound.portDown(sw.dpId, port)
        }
        timeStopped = new Date()
    }
    
    boolean isRunning() {
        return running
    }
}