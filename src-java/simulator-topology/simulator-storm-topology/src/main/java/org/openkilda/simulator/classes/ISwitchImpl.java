/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.simulator.classes;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;
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
    private SwitchChangeType state = SwitchChangeType.DEACTIVATED;
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
    public void modState(SwitchChangeType state) throws SimulatorException {
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
        state = SwitchChangeType.ACTIVATED;
    }

    @Override
    public void deactivate() {
        state = SwitchChangeType.DEACTIVATED;
    }

    @Override
    public boolean isActive() {
        return state == SwitchChangeType.ACTIVATED;
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
    public void delFlow(long cookie) {
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
