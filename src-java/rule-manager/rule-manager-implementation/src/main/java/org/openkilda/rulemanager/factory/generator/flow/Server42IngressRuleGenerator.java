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

package org.openkilda.rulemanager.factory.generator.flow;

import static java.lang.String.format;
import static org.openkilda.rulemanager.Constants.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.Oxm;
import org.openkilda.rulemanager.factory.RuleGenerator;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
public abstract class Server42IngressRuleGenerator implements RuleGenerator {

    protected RuleManagerConfig config;
    protected final FlowPath flowPath;
    protected final Flow flow;
    protected final FlowTransitEncapsulation encapsulation;
    protected final SwitchProperties switchProperties;

    protected CopyFieldAction buildServer42CopyFirstTimestamp() {
        return CopyFieldAction.builder()
                .numberOfBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .srcOffset(0)
                .dstOffset(0)
                .oxmSrcHeader(Oxm.NOVIFLOW_TX_TIMESTAMP)
                .oxmDstHeader(Oxm.NOVIFLOW_UDP_PAYLOAD_OFFSET)
                .build();
    }

    protected FlowEndpoint getIngressEndpoint(SwitchId switchId) {
        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        if (!ingressEndpoint.getSwitchId().equals(switchId)) {
            throw new IllegalArgumentException(format("Path %s has ingress endpoint %s with switchId %s. But switchId "
                            + "must be equal to target switchId %s", flowPath.getPathId(), ingressEndpoint,
                    ingressEndpoint.getSwitchId(), switchId));
        }
        return ingressEndpoint;
    }
}
