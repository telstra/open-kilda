/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorPointStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpData;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowPathStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowSegmentCookieConverter;
import org.openkilda.persistence.ferma.frames.converters.MeterIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathComputationStrategyConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;

import java.util.List;

public abstract class FlowEventDumpFrame extends KildaBaseVertexFrame implements FlowEventDumpData {
    public static final String FRAME_LABEL = "flow_dump";
    public static final String TASK_ID_PROPERTY = "task_id";
    public static final String TYPE_PROPERTY = "type";

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract String getTaskId();

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract void setTaskId(String taskId);

    @Override
    @Property("flow_id")
    public abstract String getFlowId();

    @Override
    @Property("flow_id")
    public abstract void setFlowId(String flowId);

    @Override
    @Property(TYPE_PROPERTY)
    public abstract String getType();

    @Override
    @Property(TYPE_PROPERTY)
    public abstract void setType(String type);

    @Override
    @Property("bandwidth")
    public abstract long getBandwidth();

    @Override
    @Property("bandwidth")
    public abstract void setBandwidth(long bandwidth);

    @Override
    @Property("ignoreBandwidth")
    public abstract boolean isIgnoreBandwidth();

    @Override
    @Property("ignoreBandwidth")
    public abstract void setIgnoreBandwidth(boolean ignoreBandwidth);

    @Override
    @Property("strict_bandwidth")
    public abstract boolean isStrictBandwidth();

    @Override
    @Property("strict_bandwidth")
    public abstract void setStrictBandwidth(boolean strictBandwidth);

    @Override
    @Property("forward_cookie")
    @Convert(FlowSegmentCookieConverter.class)
    public abstract FlowSegmentCookie getForwardCookie();

    @Override
    @Property("forward_cookie")
    @Convert(FlowSegmentCookieConverter.class)
    public abstract void setForwardCookie(FlowSegmentCookie forwardCookie);

    @Override
    @Property("reverse_cookie")
    @Convert(FlowSegmentCookieConverter.class)
    public abstract FlowSegmentCookie getReverseCookie();

    @Override
    @Property("reverse_cookie")
    @Convert(FlowSegmentCookieConverter.class)
    public abstract void setReverseCookie(FlowSegmentCookie reverseCookie);

    @Override
    @Property("src_switch")
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSourceSwitch();

    @Override
    @Property("src_switch")
    @Convert(SwitchIdConverter.class)
    public abstract void setSourceSwitch(SwitchId sourceSwitch);

    @Override
    @Property("dst_switch")
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getDestinationSwitch();

    @Override
    @Property("dst_switch")
    @Convert(SwitchIdConverter.class)
    public abstract void setDestinationSwitch(SwitchId destinationSwitch);

    @Override
    @Property("src_port")
    public abstract int getSourcePort();

    @Override
    @Property("src_port")
    public abstract void setSourcePort(int sourcePort);

    @Override
    @Property("dst_port")
    public abstract int getDestinationPort();

    @Override
    @Property("dst_port")
    public abstract void setDestinationPort(int destinationPort);

    @Override
    @Property("src_vlan")
    public abstract int getSourceVlan();

    @Override
    @Property("src_vlan")
    public abstract void setSourceVlan(int sourceVlan);

    @Override
    @Property("dst_vlan")
    public abstract int getDestinationVlan();

    @Override
    @Property("dst_vlan")
    public abstract void setDestinationVlan(int destinationVlan);

    @Override
    @Property("src_inner_vlan")
    public abstract Integer getSourceInnerVlan();

    @Override
    @Property("src_inner_vlan")
    public abstract void setSourceInnerVlan(Integer sourceInnerVlan);

    @Override
    @Property("dst_inner_vlan")
    public abstract Integer getDestinationInnerVlan();

    @Override
    @Property("dst_inner_vlan")
    public abstract void setDestinationInnerVlan(Integer destinationInnerVlan);

    @Override
    @Property("forward_meter_id")
    @Convert(MeterIdConverter.class)
    public abstract MeterId getForwardMeterId();

    @Override
    @Property("forward_meter_id")
    @Convert(MeterIdConverter.class)
    public abstract void setForwardMeterId(MeterId forwardMeterId);

    @Override
    @Property("reverse_meter_id")
    @Convert(MeterIdConverter.class)
    public abstract MeterId getReverseMeterId();

    @Override
    @Property("reverse_meter_id")
    @Convert(MeterIdConverter.class)
    public abstract void setReverseMeterId(MeterId reverseMeterId);

    @Override
    @Property("diverse_group_id")
    public abstract String getDiverseGroupId();

    @Override
    @Property("diverse_group_id")
    public abstract void setDiverseGroupId(String diverseGroupId);

    @Override
    @Property("affinity_group_id")
    public abstract String getAffinityGroupId();

    @Override
    @Property("affinity_group_id")
    public abstract void setAffinityGroupId(String affinityGroupId);

    @Override
    @Property("forward_path")
    public abstract String getForwardPath();

    @Override
    @Property("forward_path")
    public abstract void setForwardPath(String forwardPath);

    @Override
    @Property("reverse_path")
    public abstract String getReversePath();

    @Override
    @Property("reverse_path")
    public abstract void setReversePath(String reversePath);

    @Override
    @Property("forward_status")
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getForwardStatus();

    @Override
    @Property("forward_status")
    @Convert(FlowPathStatusConverter.class)
    public abstract void setForwardStatus(FlowPathStatus forwardStatus);

    @Override
    @Property("reverse_status")
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getReverseStatus();

    @Override
    @Property("reverse_status")
    @Convert(FlowPathStatusConverter.class)
    public abstract void setReverseStatus(FlowPathStatus reverseStatus);

    @Override
    @Property("allocate_protected_path")
    public abstract Boolean isAllocateProtectedPath();

    @Override
    @Property("allocate_protected_path")
    public abstract void setAllocateProtectedPath(Boolean allocateProtectedPath);

    @Override
    @Property("pinned")
    public abstract Boolean isPinned();

    @Override
    @Property("pinned")
    public abstract void setPinned(Boolean pinned);

    @Override
    @Property("periodic_pings")
    public abstract Boolean isPeriodicPings();

    @Override
    @Property("periodic_pings")
    public abstract void setPeriodicPings(Boolean periodicPings);

    @Override
    @Property("encapsulation_type")
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract FlowEncapsulationType getEncapsulationType();

    @Override
    @Property("encapsulation_type")
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract void setEncapsulationType(FlowEncapsulationType encapsulationType);

    @Override
    @Property("path_computation_strategy")
    @Convert(PathComputationStrategyConverter.class)
    public abstract PathComputationStrategy getPathComputationStrategy();

    @Override
    @Property("path_computation_strategy")
    @Convert(PathComputationStrategyConverter.class)
    public abstract void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

    @Override
    @Property("max_latency")
    public abstract Long getMaxLatency();

    @Override
    @Property("max_latency")
    public abstract void setMaxLatency(Long maxLatency);

    @Override
    @Property("max_latency_tier2")
    public abstract Long getMaxLatencyTier2();

    @Override
    @Property("max_latency_tier2")
    public abstract void setMaxLatencyTier2(Long maxLatencyTier2);

    @Override
    @Property("priority")
    public abstract Integer getPriority();

    @Override
    @Property("priority")
    public abstract void setPriority(Integer priority);

    @Override
    @Property("mirror_point_statuses")
    public abstract List<MirrorPointStatus> getMirrorPointStatuses();

    @Override
    @Property("mirror_point_statuses")
    public abstract void setMirrorPointStatuses(List<MirrorPointStatus> mirrorPointStatuses);

    @Override
    @Property("loop_switch_id")
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getLoopSwitchId();

    @Override
    @Property("loop_switch_id")
    @Convert(SwitchIdConverter.class)
    public abstract void setLoopSwitchId(SwitchId switchId);
}
