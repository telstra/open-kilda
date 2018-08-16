package org.openkilda.simulator.interfaces;

import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.simulator.classes.IPortImpl;
import org.openkilda.simulator.classes.SimulatorException;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Map;

public interface ISwitch {
    void modState(SwitchState state) throws SimulatorException;

    void activate();

    void deactivate();

    boolean isActive();

    int getControlPlaneLatency();

    void setControlPlaneLatency(int controlPlaneLatency);

    DatapathId getDpid();

    String getDpidAsString();

    void setDpid(DatapathId dpid);

    void setDpid(SwitchId dpid);

    List<IPortImpl> getPorts();

    IPortImpl getPort(int portNum) throws SimulatorException;

    int addPort(IPortImpl port) throws SimulatorException;

    int getMaxPorts();

    void setMaxPorts(int maxPorts);

    Map<Long, IFlow> getFlows();

    IFlow getFlow(long cookie) throws SimulatorException;

    void addFlow(IFlow flow) throws SimulatorException;

    void modFlow(IFlow flow) throws SimulatorException;

    void delFlow(long cookie) throws SimulatorException;

    List<PortStatsEntry> getPortStats();

    PortStatsEntry getPortStats(int portNum);
}
