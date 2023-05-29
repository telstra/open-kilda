/* Copyright 2023 Telstra Open Source
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
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.history.DumpType;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;
import org.openkilda.model.history.HaFlowEventDump.HaFlowPathDump;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.DumpTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.HaFlowPathDumpConverter;
import org.openkilda.persistence.ferma.frames.converters.HaSubFlowDumpWrapperConverter;
import org.openkilda.persistence.ferma.frames.converters.PathComputationStrategyConverter;

import com.syncleus.ferma.annotations.Property;

public abstract class HaFlowEventDumpFrame extends KildaBaseVertexFrame implements HaFlowEventDumpData {

    public static final String FRAME_LABEL = "ha_flow_event_dump";

    public static final String AFFINITY_GROUP_ID_PROPERTY = "affinity_group_id";
    public static final String ALLOCATE_PROTECTED_PATH_PROPERTY = "allocate_protected_path";
    public static final String DESCRIPTION_PROPERTY = "description";
    public static final String DIVERSE_GROUP_ID_PROPERTY = "diverse_group_id";
    public static final String DUMP_TYPE_PROPERTY = "dump_type";
    public static final String ENCAPSULATION_TYPE_PROPERTY = "encapsulation_type";
    public static final String FORWARD_PATH_PROPERTY = "forward_path";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String HA_SUB_FLOWS_PROPERTY = "ha_sub_flows";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String MAXIMUM_BANDWIDTH_PROPERTY = "maximum_bandwidth";
    public static final String MAX_LATENCY_PROPERTY = "max_latency";
    public static final String MAX_LATENCY_TIER_2_PROPERTY = "max_latency_tier2";
    public static final String PATH_COMPUTATION_STRATEGY_PROPERTY = "path_computation_strategy";
    public static final String PERIODIC_PINGS_PROPERTY = "periodic_pings";
    public static final String PINNED_PROPERTY = "pinned";
    public static final String PRIORITY_PROPERTY = "priority";
    public static final String SHARED_INNER_VLAN_PROPERTY = "shared_inner_vlan";
    public static final String SHARED_OUTER_VLAN_PROPERTY = "shared_outer_vlan";
    public static final String SHARED_PORT_PROPERTY = "shared_port";
    public static final String SHARED_SWITCH_ID_PROPERTY = "shared_switch_id";
    public static final String STATUS_PROPERTY = "status";
    public static final String STRICT_BANDWIDTH_PROPERTY = "strict_bandwidth";
    public static final String TASK_ID_PROPERTY = "task_id";
    public static final String FLOW_TIME_CREATE_PROPERTY = "flow_time_create";
    public static final String FLOW_TIME_MODIFY_PROPERTY = "flow_time_modify";
    public static final String REVERSE_PATH_PROPERTY = "reverse_path";
    public static final String PROTECTED_FORWARD_PATH_PROPERTY = "protected_forward_path";
    public static final String PROTECTED_REVERSE_PATH_PROPERTY = "protected_reverse_path";

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract String getTaskId();

    @Override
    @Property(TASK_ID_PROPERTY)
    public abstract void setTaskId(String taskId);

    @Override
    @Property(DUMP_TYPE_PROPERTY)
    @Convert(DumpTypeConverter.class)
    public abstract DumpType getDumpType();

    @Override
    @Property(DUMP_TYPE_PROPERTY)
    @Convert(DumpTypeConverter.class)
    public abstract void setDumpType(DumpType type);

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract void setHaFlowId(String haFlowId);

    @Override
    @Property(SHARED_SWITCH_ID_PROPERTY)
    public abstract String getSharedSwitchId();

    @Override
    @Property(SHARED_SWITCH_ID_PROPERTY)
    public abstract void setSharedSwitchId(String switchId);

    @Override
    @Property(SHARED_PORT_PROPERTY)
    public abstract Integer getSharedPort();

    @Override
    @Property(SHARED_PORT_PROPERTY)
    public abstract void setSharedPort(Integer port);

    @Override
    @Property(SHARED_OUTER_VLAN_PROPERTY)
    public abstract Integer getSharedOuterVlan();

    @Override
    @Property(SHARED_OUTER_VLAN_PROPERTY)
    public abstract void setSharedOuterVlan(Integer outerVlan);

    @Override
    @Property(SHARED_INNER_VLAN_PROPERTY)
    public abstract Integer getSharedInnerVlan();

    @Override
    @Property(SHARED_INNER_VLAN_PROPERTY)
    public abstract void setSharedInnerVlan(Integer innerVlan);

    @Override
    @Property(MAXIMUM_BANDWIDTH_PROPERTY)
    public abstract Long getMaximumBandwidth();

    @Override
    @Property(MAXIMUM_BANDWIDTH_PROPERTY)
    public abstract void setMaximumBandwidth(Long maximumBandwidth);

    @Override
    @Property(PATH_COMPUTATION_STRATEGY_PROPERTY)
    @Convert(PathComputationStrategyConverter.class)
    public abstract PathComputationStrategy getPathComputationStrategy();

    @Override
    @Property(PATH_COMPUTATION_STRATEGY_PROPERTY)
    @Convert(PathComputationStrategyConverter.class)
    public abstract void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

    @Override
    @Property(ENCAPSULATION_TYPE_PROPERTY)
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract FlowEncapsulationType getEncapsulationType();

    @Override
    @Property(ENCAPSULATION_TYPE_PROPERTY)
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract void setEncapsulationType(FlowEncapsulationType encapsulationType);

    @Override
    @Property(MAX_LATENCY_PROPERTY)
    public abstract Long getMaxLatency();

    @Override
    @Property(MAX_LATENCY_PROPERTY)
    public abstract void setMaxLatency(Long maxLatency);

    @Override
    @Property(MAX_LATENCY_TIER_2_PROPERTY)
    public abstract Long getMaxLatencyTier2();

    @Override
    @Property(MAX_LATENCY_TIER_2_PROPERTY)
    public abstract void setMaxLatencyTier2(Long maxLatencyTier2);

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract Boolean getIgnoreBandwidth();

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract void setIgnoreBandwidth(Boolean ignoreBandwidth);

    @Override
    @Property(PERIODIC_PINGS_PROPERTY)
    public abstract Boolean getPeriodicPings();

    @Override
    @Property(PERIODIC_PINGS_PROPERTY)
    public abstract void setPeriodicPings(Boolean periodicPings);

    @Override
    @Property(PINNED_PROPERTY)
    public abstract Boolean getPinned();

    @Override
    @Property(PINNED_PROPERTY)
    public abstract void setPinned(Boolean pinned);

    @Override
    @Property(PRIORITY_PROPERTY)
    public abstract Integer getPriority();

    @Override
    @Property(PRIORITY_PROPERTY)
    public abstract void setPriority(Integer priority);

    @Override
    @Property(STRICT_BANDWIDTH_PROPERTY)
    public abstract Boolean getStrictBandwidth();

    @Override
    @Property(STRICT_BANDWIDTH_PROPERTY)
    public abstract void setStrictBandwidth(Boolean strictBandwidth);

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract String getDescription();

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract void setDescription(String description);

    @Override
    @Property(ALLOCATE_PROTECTED_PATH_PROPERTY)
    public abstract Boolean getAllocateProtectedPath();

    @Override
    @Property(ALLOCATE_PROTECTED_PATH_PROPERTY)
    public abstract void setAllocateProtectedPath(Boolean allocateProtectedPath);

    @Override
    @Property(DIVERSE_GROUP_ID_PROPERTY)
    public abstract String getDiverseGroupId();

    @Override
    @Property(DIVERSE_GROUP_ID_PROPERTY)
    public abstract void setDiverseGroupId(String diverseGroupId);

    @Override
    @Property(AFFINITY_GROUP_ID_PROPERTY)
    public abstract String getAffinityGroupId();

    @Override
    @Property(AFFINITY_GROUP_ID_PROPERTY)
    public abstract void setAffinityGroupId(String affinityGroupId);

    @Override
    @Property(STATUS_PROPERTY)
    public abstract FlowStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
    public abstract void setStatus(FlowStatus status);

    @Override
    @Property(FLOW_TIME_CREATE_PROPERTY)
    public abstract String getFlowTimeCreate();

    @Override
    @Property(FLOW_TIME_CREATE_PROPERTY)
    public abstract void setFlowTimeCreate(String timeCreate);

    @Override
    @Property(FLOW_TIME_MODIFY_PROPERTY)
    public abstract String getFlowTimeModify();

    @Override
    @Property(FLOW_TIME_MODIFY_PROPERTY)
    public abstract void setFlowTimeModify(String timeModify);

    @Override
    @Property(HA_SUB_FLOWS_PROPERTY)
    @Convert(HaSubFlowDumpWrapperConverter.class)
    public abstract HaSubFlowDumpWrapper getHaSubFlows();

    @Override
    @Property(HA_SUB_FLOWS_PROPERTY)
    @Convert(HaSubFlowDumpWrapperConverter.class)
    public abstract void setHaSubFlows(HaSubFlowDumpWrapper haSubFlows);

    @Override
    @Property(FORWARD_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract HaFlowPathDump getForwardPath();

    @Override
    @Property(FORWARD_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract void setForwardPath(HaFlowPathDump forwardPath);

    @Override
    @Property(REVERSE_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract HaFlowPathDump getReversePath();

    @Override
    @Property(REVERSE_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract void setReversePath(HaFlowPathDump reversePath);

    @Override
    @Property(PROTECTED_FORWARD_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract HaFlowPathDump getProtectedForwardPath();

    @Override
    @Property(PROTECTED_FORWARD_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract void setProtectedForwardPath(HaFlowPathDump protectedForwardPath);

    @Override
    @Property(PROTECTED_REVERSE_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract HaFlowPathDump getProtectedReversePath();

    @Override
    @Property(PROTECTED_REVERSE_PATH_PROPERTY)
    @Convert(HaFlowPathDumpConverter.class)
    public abstract void setProtectedReversePath(HaFlowPathDump protectedReversePath);
}
