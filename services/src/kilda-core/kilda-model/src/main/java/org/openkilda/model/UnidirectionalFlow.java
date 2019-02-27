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

package org.openkilda.model;

import static java.lang.String.format;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents a unidirectional flow.
 *
 * @deprecated Must be replaced with new model entities: {@link org.openkilda.model.Flow},
 * {@link org.openkilda.model.FlowPath}
 */
@Deprecated
public class UnidirectionalFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Flow flow;
    private final FlowPath flowPath;
    private final TransitVlan transitVlan;
    private final boolean forward;

    public UnidirectionalFlow(Flow flow, FlowPath flowPath, TransitVlan transitVlan, boolean forward) {
        this.flow = flow;
        this.flowPath = flowPath;
        this.transitVlan = transitVlan;
        this.forward = forward;
    }

    public String getFlowId() {
        return flow.getFlowId();
    }

    /**
     * Set the flowId (propagate to wrapped flow, flowPath and transitVlan).
     */
    public void setFlowId(String flowId) {
        flow.setFlowId(flowId);
        flowPath.setFlowId(flowId);
        if (transitVlan != null) {
            transitVlan.setFlowId(flowId);
        }
    }

    public long getCookie() {
        return flowPath.getCookie().getValue();
    }

    public void setCookie(long cookie) {
        flowPath.setCookie(new Cookie(cookie));
    }

    public Switch getSrcSwitch() {
        return forward ? flow.getSrcSwitch() : flow.getDestSwitch();
    }

    /**
     * Set the srcSwitch (propagate to wrapped flow and flowPath).
     */
    public void setSrcSwitch(Switch srcSwitch) {
        flowPath.setSrcSwitch(srcSwitch);

        if (forward) {
            flow.setSrcSwitch(srcSwitch);
        } else {
            flow.setDestSwitch(srcSwitch);
        }
    }

    public Switch getDestSwitch() {
        return forward ? flowPath.getDestSwitch() : flow.getSrcSwitch();
    }

    /**
     * Set the destSwitch (propagate to wrapped flow and flowPath).
     */
    public void setDestSwitch(Switch destSwitch) {
        flowPath.setDestSwitch(destSwitch);

        if (forward) {
            flow.setDestSwitch(destSwitch);
        } else {
            flow.setSrcSwitch(destSwitch);
        }
    }

    public int getSrcPort() {
        return forward ? flow.getSrcPort() : flow.getDestPort();
    }

    /**
     * Set the srcPort (propagate to wrapped flow).
     */
    public void setSrcPort(int srcPort) {
        if (forward) {
            flow.setSrcPort(srcPort);
        } else {
            flow.setDestPort(srcPort);
        }
    }

    public int getSrcVlan() {
        return forward ? flow.getSrcVlan() : flow.getDestVlan();
    }

    /**
     * Set the srcVlan (propagate to wrapped flow).
     */
    public void setSrcVlan(int srcVlan) {
        if (forward) {
            flow.setSrcVlan(srcVlan);
        } else {
            flow.setDestVlan(srcVlan);
        }
    }

    public int getDestPort() {
        return forward ? flow.getDestPort() : flow.getSrcPort();
    }

    /**
     * Set the destPort (propagate to wrapped flow).
     */
    public void setDestPort(int destPort) {
        if (forward) {
            flow.setDestPort(destPort);
        } else {
            flow.setSrcPort(destPort);
        }
    }

    public int getDestVlan() {
        return forward ? flow.getDestVlan() : flow.getSrcVlan();
    }

    /**
     * Set the destVlan (propagate to wrapped flow).
     */
    public void setDestVlan(int destVlan) {
        if (forward) {
            flow.setDestVlan(destVlan);
        } else {
            flow.setSrcVlan(destVlan);
        }
    }

    public long getBandwidth() {
        return flow.getBandwidth();
    }

    public void setBandwidth(long bandwidth) {
        flow.setBandwidth(bandwidth);
    }

    public String getDescription() {
        return flow.getDescription();
    }

    public void setDescription(String description) {
        flow.setDescription(description);
    }

    public int getTransitVlan() {
        return transitVlan != null ? transitVlan.getVlan() : 0;
    }

    /**
     * Set the transit vlan. Allowed only if the flow has been initialized with TransitVlan.
     */
    public void setTransitVlan(int vlan) {
        if (transitVlan == null) {
            throw new IllegalStateException("Transit Vlan entity hasn't been initialized.");
        }

        transitVlan.setVlan(vlan);
    }

    public Long getMeterId() {
        return Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null);
    }

    public void setMeterId(Long meterId) {
        flowPath.setMeterId(meterId != null ? new MeterId(meterId) : null);
    }

    public boolean isIgnoreBandwidth() {
        return flow.isIgnoreBandwidth();
    }

    public void setIgnoreBandwidth(boolean ignoreBandwidth) {
        flow.setIgnoreBandwidth(ignoreBandwidth);
    }

    public boolean isPeriodicPings() {
        return flow.isPeriodicPings();
    }

    public void setPeriodicPings(boolean periodicPings) {
        flow.setPeriodicPings(periodicPings);
    }

    public FlowStatus getStatus() {
        return flow.getStatus();
    }

    /**
     * Set the status (propagate to wrapped flow and flowPath).
     */
    public void setStatus(FlowStatus status) {
        flow.setStatus(status);

        switch (status) {
            case UP:
                flowPath.setStatus(FlowPathStatus.ACTIVE);
                break;
            case DOWN:
                flowPath.setStatus(FlowPathStatus.INACTIVE);
                break;
            case IN_PROGRESS:
                flowPath.setStatus(FlowPathStatus.IN_PROGRESS);
                break;
            default:
                throw new IllegalArgumentException(format("Unsupported status value: %s", status));
        }
    }

    public Instant getTimeModify() {
        return flow.getTimeModify();
    }

    public void setTimeModify(Instant timeModify) {
        flow.setTimeModify(timeModify);
    }

    /**
     * Checks whether a flow is forward.
     *
     * @return boolean flag
     */
    public boolean isForward() {
        boolean isForward = flowPath.getCookie().isMarkedAsForward();
        boolean isReversed = flowPath.getCookie().isMarkedAsReversed();

        if (isForward && isReversed) {
            throw new IllegalArgumentException(
                    "Invalid cookie flags combinations - it mark as forward and reverse flow at same time.");
        }

        return isForward;
    }

    /**
     * Checks whether a flow is reverse.
     *
     * @return boolean flag
     */
    public boolean isReverse() {
        return !isForward();
    }

    /**
     * Checks whether the flow is through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return getSrcSwitch().getSwitchId().equals(getDestSwitch().getSwitchId());
    }

    public boolean isActive() {
        return getStatus() == FlowStatus.UP;
    }

    public Flow getFlowEntity() {
        return flow;
    }

    public FlowPath getFlowPath() {
        return flowPath;
    }

    public TransitVlan getTransitVlanEntity() {
        return transitVlan;
    }

    public String getGroupId() {
        return flow.getGroupId();
    }

    public void setGroupId(String groupId) {
        flow.setGroupId(groupId);
    }

    public Instant getTimeCreate() {
        return flow.getTimeCreate();
    }

    public void setTimeCreate(Instant timeCreate) {
        flow.setTimeCreate(timeCreate);
    }

    public Integer getMaxLatency() {
        return flow.getMaxLatency();
    }

    public void setMaxLatency(Integer maxLatency) {
        flow.setMaxLatency(maxLatency);
    }

    public Integer getPriority() {
        return flow.getPriority();
    }

    public void setPriority(Integer priority) {
        flow.setPriority(priority);
    }
}
