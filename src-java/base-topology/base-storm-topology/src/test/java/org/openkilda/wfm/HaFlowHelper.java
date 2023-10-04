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

package org.openkilda.wfm;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.history.model.HaFlowEventData;

import com.google.common.collect.Sets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class HaFlowHelper {
    public static final String DETAILS = "details";
    public static final HaFlowEventData.Event EVENT_CREATE = HaFlowEventData.Event.CREATE;
    public static final String ACTOR = "actor";
    public static final String CORRELATION_ID = "correlation id";
    public static final String FAKE_TIMESTAMP = "fake timestamp";
    public static final String HA_FLOW_ID = "HA flow ID";
    public static final HaFlowEventData.Initiator INITIATOR = HaFlowEventData.Initiator.NB;
    public static final String HISTORY_ACTION = "Ha flow history action";
    public static final Integer SHARED_PORT = 1;
    public static final Integer SHARED_OUTER_VLAN = 1000;
    public static final Integer SHARED_INNER_VLAN = 2000;
    public static final Long MAXIMUM_BANDWIDTH = 100_000L;
    public static final PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST;
    public static final FlowEncapsulationType FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;
    public static final Long MAX_LATENCY = 42L;
    public static final Long MAX_LATENCY_TIER_2 = 84L;
    public static final Boolean IGNORE_BANDWIDTH = false;
    public static final Boolean PERIODIC_PINGS = false;
    public static final Boolean PINNED = false;
    public static final Integer PRIORITY = 455;
    public static final String STATUS_INFO = "Status info";
    public static final Boolean STRICT_BANDWIDTH = false;
    public static final String DESCRIPTION = "HA flow description";
    public static final Boolean ALLOCATE_PROTECTED_PATH = true;
    public static final FlowStatus FLOW_STATUS = FlowStatus.UP;
    public static final String AFFINITY_GROUP_ID = "affinity group ID";
    public static final String DIVERSE_GROUP_ID = "diverse group ID";
    public static final Switch SHARED_SWITCH = Switch.builder().switchId(new SwitchId("00:00:01")).build();
    public static final Switch SUBFLOW_SWITCH_A = Switch.builder().switchId(new SwitchId("00:00:02")).build();
    public static final Switch SUBFLOW_SWITCH_B = Switch.builder().switchId(new SwitchId("00:00:03")).build();
    public static final Instant TIME_CREATE = Instant.now().minus(1, ChronoUnit.HOURS);
    public static final Instant TIME_MODIFY = Instant.now();
    public static final FlowPathStatus FLOW_PATH_STATUS = FlowPathStatus.ACTIVE;
    public static final String HA_SUB_FLOW_ID = "HA sub flow ID";
    public static final SwitchId ENDPOINT_SWITCH_ID = new SwitchId("00:01");
    public static final Integer ENDPOINT_PORT = 1;
    public static final Integer ENDPOINT_VLAN = 1000;
    public static final Integer ENDPOINT_INNER_VLAN = 2000;
    public static final long PATH_BANDWIDTH = 50_000L;
    public static final FlowStatus SUBFLOW_STATUS = FlowStatus.UP;
    public static final int SUBFLOW_INNER_VLAN_A = 555;
    public static final int SUBFLOW_VLAN_A = 666;
    public static final int SUBFLOW_PORT_A = 42;
    public static final int SUBFLOW_INNER_VLAN_B = 777;
    public static final int SUBFLOW_VLAN_B = 888;
    public static final int SUBFLOW_PORT_B = 24;
    public static final String HA_SUBFLOW_DESCRIPTION_B = "HA subflow description B";
    public static final String HA_SUBFLOW_DESCRIPTION_A = "HA subflow description A";

    private HaFlowHelper() {
    }

    /**
     * Creates an HA-flow with paths and subflows configured.
     * @return an HA-flow suitable for testing with all parameters set.
     */
    public static HaFlow createHaFlow() {
        HaFlow result = new HaFlow(HaFlowHelper.HA_FLOW_ID,
                HaFlowHelper.SHARED_SWITCH,
                HaFlowHelper.SHARED_PORT,
                HaFlowHelper.SHARED_OUTER_VLAN,
                HaFlowHelper.SHARED_INNER_VLAN,
                HaFlowHelper.MAXIMUM_BANDWIDTH,
                HaFlowHelper.PATH_COMPUTATION_STRATEGY,
                HaFlowHelper.FLOW_ENCAPSULATION_TYPE,
                HaFlowHelper.MAX_LATENCY,
                HaFlowHelper.MAX_LATENCY_TIER_2,
                HaFlowHelper.IGNORE_BANDWIDTH,
                HaFlowHelper.PERIODIC_PINGS,
                HaFlowHelper.PINNED,
                HaFlowHelper.PRIORITY,
                HaFlowHelper.STRICT_BANDWIDTH,
                HaFlowHelper.DESCRIPTION,
                HaFlowHelper.ALLOCATE_PROTECTED_PATH,
                HaFlowHelper.FLOW_STATUS,
                HaFlowHelper.STATUS_INFO,
                HaFlowHelper.AFFINITY_GROUP_ID,
                HaFlowHelper.DIVERSE_GROUP_ID);

        HaFlowPath forward = createHaFlowPath("forward ID", FlowPathDirection.FORWARD);
        HaFlowPath reverse = createHaFlowPath("reverse ID", FlowPathDirection.REVERSE);

        result.setForwardPath(forward);
        result.setReversePath(reverse);
        result.addPaths(forward, reverse);

        HaSubFlow a = HaSubFlow.builder()
                .description(HA_SUBFLOW_DESCRIPTION_A)
                .status(SUBFLOW_STATUS)
                .endpointInnerVlan(SUBFLOW_INNER_VLAN_A)
                .endpointVlan(SUBFLOW_VLAN_A)
                .endpointSwitch(SUBFLOW_SWITCH_A)
                .endpointPort(SUBFLOW_PORT_A)
                .haSubFlowId(result.getHaFlowId() + "_a")
                .build();

        HaSubFlow b = HaSubFlow.builder()
                .description(HA_SUBFLOW_DESCRIPTION_B)
                .status(SUBFLOW_STATUS)
                .endpointInnerVlan(SUBFLOW_INNER_VLAN_B)
                .endpointVlan(SUBFLOW_VLAN_B)
                .endpointSwitch(SUBFLOW_SWITCH_B)
                .endpointPort(SUBFLOW_PORT_B)
                .haSubFlowId(result.getHaFlowId() + "_b")
                .build();

        result.setHaSubFlows(Sets.newHashSet(a, b));

        return result;
    }

    private static HaFlowPath createHaFlowPath(String pathId, FlowPathDirection direction) {
        return HaFlowPath.builder()
                .haPathId(new PathId(pathId))
                .sharedSwitch(HaFlowHelper.SHARED_SWITCH)
                .bandwidth(HaFlowHelper.PATH_BANDWIDTH)
                .cookie(FlowSegmentCookie.builder().direction(direction).build())
                .build();
    }
}
