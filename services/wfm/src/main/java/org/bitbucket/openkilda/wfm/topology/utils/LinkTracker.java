package org.bitbucket.openkilda.wfm.topology.utils;

import org.bitbucket.openkilda.messaging.info.event.PathNode;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by carmine on 5/14/17.
 */
public class LinkTracker implements Serializable {

    /**
     * SwitchID -> PortID, Transmitted Frames
     */
    protected ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> state = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, AtomicInteger> getSwitchPorts(String switchID) {
        return state.get(switchID);
    }

    public ConcurrentHashMap<String, AtomicInteger> getOrNewSwitchPorts(String switchID) {
        return state.computeIfAbsent(switchID, k -> new ConcurrentHashMap<>());
    }

    /** for use in foreach */
    public ConcurrentHashMap.KeySetView<String, ConcurrentHashMap<String, AtomicInteger>> getSwitches() {
        return state.keySet();
    }

    public void clearCountOfSentPackets(String switchId, String portNo) {
        ConcurrentHashMap<String, AtomicInteger> ports = state.get(switchId);
        if (ports != null) {
            AtomicInteger packets = ports.get(portNo);
            if (packets != null) {
                packets.set(0);
            }
        }
    }
}
