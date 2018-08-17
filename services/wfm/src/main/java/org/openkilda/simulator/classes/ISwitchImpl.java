package org.openkilda.simulator.classes;

import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.simulator.interfaces.IFlow;
import org.openkilda.simulator.interfaces.ISwitch;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ISwitchImpl implements ISwitch {
    private DatapathId dpid;
    private List<IPortImpl> ports = new ArrayList<>();
    private Map<Long, IFlow> flows = new HashMap<>();
    private int controlPlaneLatency = 0;
    private SwitchState state = SwitchState.DEACTIVATED;
    private int maxPorts = 0;

    public ISwitchImpl() throws SimulatorException {
        this(new SwitchId("00"));
    }

    public ISwitchImpl(SwitchId dpid) throws SimulatorException {
        this(dpid, 0, PortStateType.DOWN);
    }

    public ISwitchImpl(SwitchId dpid, int numOfPorts, PortStateType portState) throws SimulatorException {
        setDpid(dpid);
        maxPorts = numOfPorts;
        int count = 0;
        while (count < numOfPorts) {
            IPortImpl port = new IPortImpl(this, portState, ports.size());
            addPort(port);
            count++;
        }
    }

    @Override
    public void modState(SwitchState state) throws SimulatorException {
        this.state = state;

        switch (state) {
            case ADDED:
                break;
            case ACTIVATED:
                activate();
                break;
            case DEACTIVATED:
            case REMOVED:
                deactivate();
                break;
            case CHANGED:
                throw new SimulatorException("Received modState of CHANGED, no idea why");
            default:
                throw new SimulatorException(String.format("Unknown state %s", state));
        }
    }

    @Override
    public void activate() {
        state = SwitchState.ACTIVATED;
    }

    @Override
    public void deactivate() {
        state = SwitchState.DEACTIVATED;
    }

    @Override
    public boolean isActive() {
        return state == SwitchState.ACTIVATED ? true : false;
    }

    @Override
    public int getControlPlaneLatency() {
        return controlPlaneLatency;
    }

    @Override
    public void setControlPlaneLatency(int controlPlaneLatency) {
        this.controlPlaneLatency = controlPlaneLatency;
    }

    @Override
    public DatapathId getDpid() {
        return dpid;
    }

    @Override
    public String getDpidAsString() {
        return dpid.toString();
    }

    @Override
    public void setDpid(DatapathId dpid) {
        this.dpid = dpid;
    }

    @Override
    public void setDpid(SwitchId dpid) {
        this.dpid = DatapathId.of(dpid.toString());
    }

    @Override
    public List<IPortImpl> getPorts() {
        return ports;
    }

    @Override
    public IPortImpl getPort(int portNum) throws SimulatorException {
        try {
            return ports.get(portNum);
        } catch (IndexOutOfBoundsException e) {
            throw new SimulatorException(String.format("Port %d is not defined on %s", portNum, getDpidAsString()));
        }
    }

    @Override
    public int addPort(IPortImpl port) throws SimulatorException {
        if (ports.size() < maxPorts) {
            ports.add(port);
        } else {
            throw new SimulatorException(String.format("Switch already has reached maxPorts of %d"
                    + "", maxPorts));
        }
        return port.getNumber();
    }

    @Override
    public int getMaxPorts() {
        return maxPorts;
    }

    @Override
    public void setMaxPorts(int maxPorts) {
        this.maxPorts = maxPorts;
    }

    @Override
    public Map<Long, IFlow> getFlows() {
        return flows;
    }

    @Override
    public IFlow getFlow(long cookie) throws SimulatorException {
        try {
            return flows.get(cookie);
        } catch (IndexOutOfBoundsException e) {
            throw new SimulatorException(String.format("Flow %d could not be found.", cookie));
        }
    }

    @Override
    public void addFlow(IFlow flow) throws SimulatorException {
        if (flows.containsKey(flow.getCookie())) {
            throw new SimulatorException(String.format("Flow %s already exists.", flow.toString()));
        }
        flows.put(flow.getCookie(), flow);
    }

    @Override
    public void modFlow(IFlow flow) throws SimulatorException {
        delFlow(flow.getCookie());
        addFlow(flow);
    }

    @Override
    public void delFlow(long cookie) throws SimulatorException {
        flows.remove(cookie);
    }

    @Override
    public List<PortStatsEntry> getPortStats() {
        return null;
    }

    @Override
    public PortStatsEntry getPortStats(int portNum) {
        return null;
    }
}
