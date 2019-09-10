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

import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents a unidirectional flow with transit vlan encapsulation.
 *
 * @deprecated Must be replaced with new model entities: {@link org.openkilda.model.Flow},
 * {@link org.openkilda.model.FlowPath}
 */
@ToString
@Deprecated
public class UnidirectionalFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final FlowPath flowPath;
    private final EncapsulationId encapsulationId;
    private final boolean forward;

    public UnidirectionalFlow(FlowPath flowPath, EncapsulationId encapsulationId, boolean forward) {
        this.flowPath = flowPath;
        this.encapsulationId = encapsulationId;
        this.forward = forward;
    }

    public String getFlowId() {
        return getFlow().getFlowId();
    }

    public long getCookie() {
        return flowPath.getCookie().getValue();
    }

    public Switch getSrcSwitch() {
        return forward ? getFlow().getSrcSwitch() : getFlow().getDestSwitch();
    }

    public Switch getDestSwitch() {
        return forward ? getFlow().getDestSwitch() : getFlow().getSrcSwitch();
    }

    public int getSrcPort() {
        return forward ? getFlow().getSrcPort() : getFlow().getDestPort();
    }

    public int getSrcVlan() {
        return forward ? getFlow().getSrcVlan() : getFlow().getDestVlan();
    }

    public int getDestPort() {
        return forward ? getFlow().getDestPort() : getFlow().getSrcPort();
    }

    public int getDestVlan() {
        return forward ? getFlow().getDestVlan() : getFlow().getSrcVlan();
    }

    public long getBandwidth() {
        return getFlow().getBandwidth();
    }

    public String getDescription() {
        return getFlow().getDescription();
    }

    public EncapsulationId getEncapsulationId() {
        return encapsulationId;
    }

    public Long getMeterId() {
        return Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null);
    }

    public boolean isIgnoreBandwidth() {
        return getFlow().isIgnoreBandwidth();
    }

    public boolean isPeriodicPings() {
        return getFlow().isPeriodicPings();
    }

    public boolean isAllocateProtectedPath() {
        return getFlow().isAllocateProtectedPath();
    }

    public void setAllocateProtectedPath(boolean allocateProtectedPath) {
        getFlow().setAllocateProtectedPath(allocateProtectedPath);
    }

    public FlowStatus getStatus() {
        return getFlow().getStatus();
    }

    public boolean isPinned() {
        return getFlow().isPinned();
    }

    /**
     * Get detect connected devices flags.
     */
    public DetectConnectedDevices getDetectConnectedDevices() {
        if (forward) {
            return getFlow().getDetectConnectedDevices();
        } else {
            // reverse flags
            return new DetectConnectedDevices(
                    getFlow().getDetectConnectedDevices().isDstLldp(),
                    getFlow().getDetectConnectedDevices().isDstArp(),
                    getFlow().getDetectConnectedDevices().isSrcLldp(),
                    getFlow().getDetectConnectedDevices().isSrcArp());
        }
    }

    /**
     * Set the status (propagate to wrapped flow and flowPath).
     */
    public void setStatus(FlowStatus status) {
        getFlow().setStatus(status);
        flowPath.setStatusLikeFlow(status);
    }

    public Instant getTimeModify() {
        return getFlow().getTimeModify();
    }

    /**
     * Checks whether a flow is forward.
     *
     * @return boolean flag
     */
    public boolean isForward() {
        return getFlow().isForward(flowPath);
    }

    /**
     * Checks whether a flow is reverse.
     *
     * @return boolean flag
     */
    public boolean isReverse() {
        return getFlow().isReverse(flowPath);
    }

    public boolean isActive() {
        return getStatus() == FlowStatus.UP;
    }

    public Flow getFlow() {
        return flowPath.getFlow();
    }

    public FlowPath getFlowPath() {
        return flowPath;
    }

    public String getGroupId() {
        return getFlow().getGroupId();
    }

    public Instant getTimeCreate() {
        return getFlow().getTimeCreate();
    }

    public Integer getMaxLatency() {
        return getFlow().getMaxLatency();
    }

    public Integer getPriority() {
        return getFlow().getPriority();
    }

    public FlowEncapsulationType getEncapsulationType() {
        return getFlow().getEncapsulationType();
    }
}
