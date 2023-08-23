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

package org.openkilda.pce;

import static com.google.common.collect.Sets.newHashSet;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * This holder class allows to use short common signatures for methods taking simple and HA flows.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowParameters {
    public FlowParameters(Flow flow) {
        this(flow.isIgnoreBandwidth(), flow.getBandwidth(), flow.getEncapsulationType(), flow.getFlowId(), null,
                flow.getYFlowId(), flow.getDiverseGroupId(), flow.getAffinityGroupId(),
                newHashSet(flow.getSrcSwitchId(), flow.getDestSwitchId()), true, false);
    }

    public FlowParameters(HaFlow haFlow) {
        this(haFlow.isIgnoreBandwidth(), haFlow.getMaximumBandwidth(), haFlow.getEncapsulationType(), null,
                haFlow.getHaFlowId(), null, haFlow.getDiverseGroupId(), haFlow.getAffinityGroupId(),
                getSwitchIds(haFlow), false, true);
    }

    boolean ignoreBandwidth;
    long bandwidth;
    FlowEncapsulationType encapsulationType;
    String flowId;
    String haFlowId;
    String yFlowId;
    String diverseGroupId;
    String affinityGroupId;
    @NonNull
    Set<SwitchId> terminatingSwitchIds;

    boolean commonFlow;
    boolean haFlow;

    private static Set<SwitchId> getSwitchIds(HaFlow haFlow) {
        Set<SwitchId> result = haFlow.getHaSubFlows().stream()
                .map(HaSubFlow::getEndpointSwitchId)
                .collect(Collectors.toSet());
        result.add(haFlow.getSharedSwitchId());
        return result;
    }
}
