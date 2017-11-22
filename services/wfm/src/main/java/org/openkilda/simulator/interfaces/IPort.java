package org.openkilda.simulator.interfaces;

import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.projectfloodlight.openflow.types.DatapathId;

public interface IPort {
    void enable();
    void disable();
    void block();
    void unblock();
    boolean isActive();
    boolean isForwarding();
    int getNumber();
    void setIsl(DatapathId peerSwitch, int peerPortNum);
    boolean isActiveIsl();
    PortStatsEntry getStats();
    void setLatency(int latency);
    int getLatency();
    String getPeerSwitch();
    void setPeerSwitch(String peerSwitch);
    void setPeerPortNum(int peerPortNum);
    int getPeerPortNum();
}
