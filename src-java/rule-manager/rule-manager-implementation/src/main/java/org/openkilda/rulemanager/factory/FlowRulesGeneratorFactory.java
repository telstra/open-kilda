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

package org.openkilda.rulemanager.factory;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathSegment;
import org.openkilda.rulemanager.factory.generator.flow.EgressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.SingleTableIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.TransitRuleGenerator;

public class FlowRulesGeneratorFactory {

    /**
     * Get ingress rule generator.
     */
    public RuleGenerator getIngressRuleGenerator(FlowPath flowPath, Flow flow) {
        PathSegment segment = flowPath.getSegments().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("No segments found for path %s", flowPath.getPathId())));
        String errorMessage = String.format("First flow(id:%s, path:%s) segment and flow path level multi-table "
                + "flag values are incompatible to each other - flow path(%s) != segment(%s)",
                flow.getFlowId(), flowPath.getPathId(),
                flowPath.isSrcWithMultiTable(), segment.isSrcWithMultiTable());
        boolean multiTable = ensureEqualMultiTableFlag(flowPath.isSrcWithMultiTable(),
                segment.isSrcWithMultiTable(), errorMessage);
        if (multiTable) {
            // todo add multiTable support
            return null;
        } else {
            return SingleTableIngressRuleGenerator.builder()
                    .flowPath(flowPath)
                    .flow(flow)
                    .build();
        }
    }

    /**
     * Get egress rule generator.
     */
    public RuleGenerator getEgressRuleGenerator(FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation) {
        if (flowPath.isOneSwitchFlow()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create egress rule for flow %s and path %s because it is one switch flow",
                    flow.getFlowId(), flowPath.getPathId()));
        }

        if (flowPath.getSegments().isEmpty()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create egress rule for flow %s and path %s because path segments list is empty",
                    flow.getFlowId(), flowPath.getPathId()));
        }

        return EgressRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
    }

    /**
     * Get transit rule generator.
     */
    public RuleGenerator getTransitRuleGenerator(FlowPath flowPath, FlowTransitEncapsulation encapsulation,
                                                 PathSegment firstSegment, PathSegment secondSegment) {
        if (flowPath.isOneSwitchFlow()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because it is one switch path", flowPath.getPathId()));
        }

        if (!firstSegment.getDestSwitchId().equals(secondSegment.getSrcSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because segments switch ids are different: %s, %s",
                    flowPath.getPathId(), firstSegment.getDestSwitchId(), secondSegment.getSrcSwitchId()));
        }

        return TransitRuleGenerator.builder()
                .flowPath(flowPath)
                .encapsulation(encapsulation)
                .inPort(firstSegment.getDestPort())
                .outPort(secondSegment.getSrcPort())
                .multiTable(isSegmentMultiTable(firstSegment, secondSegment))
                .build();
    }

    private boolean isSegmentMultiTable(PathSegment first, PathSegment second) {
        if (first.isDestWithMultiTable() != second.isSrcWithMultiTable()) {
            throw new IllegalStateException(
                    format("Paths segments %s and %s has different multi table flag for switch %s",
                            first, second, first.getDestSwitchId()));
        }
        return first.isDestWithMultiTable();
    }

    private boolean ensureEqualMultiTableFlag(boolean flowPathSide, boolean segmentSide, String errorMessage) {
        if (flowPathSide != segmentSide) {
            throw new IllegalArgumentException(errorMessage);
        }
        return flowPathSide;
    }
}
