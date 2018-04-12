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

package org.openkilda.wfm.topology.flow.validation;

import static java.lang.String.format;

import org.openkilda.messaging.model.Flow;
import org.openkilda.pce.cache.FlowCache;

import java.util.Optional;
import java.util.Set;

/**
 * {@code FlowValidator} performs checks against the flow validation rules.
 */
public class FlowValidator {

    private final FlowCache flowCache;

    public FlowValidator(FlowCache flowCache) {
        this.flowCache = flowCache;
    }

    /**
     * Checks a flow for endpoints' conflicts.
     *
     * @param requestedFlow a flow to check
     * @throws FlowValidationException is thrown in a case when flow endpoints conflict with existing flows.
     */
    public void checkFlowForEndpointConflicts(Flow requestedFlow) throws FlowValidationException {
        // Check the source
        Set<Flow> conflictsOnSource;
        if (requestedFlow.getSourceVlan() == 0) {
            conflictsOnSource = flowCache.getFlowsForEndpoint(
                    requestedFlow.getSourceSwitch(),
                    requestedFlow.getSourcePort());
        } else {
            conflictsOnSource = flowCache.getFlowsForEndpoint(
                    requestedFlow.getSourceSwitch(),
                    requestedFlow.getSourcePort(),
                    requestedFlow.getSourceVlan());
        }

        Optional<Flow> conflictedFlow = conflictsOnSource.stream()
                .filter(flow -> !flow.getFlowId().equals(requestedFlow.getFlowId()))
                .findAny();
        if (conflictedFlow.isPresent()) {
            throw new FlowValidationException(
                    format("The port %d on the switch '%s' has already occupied by the flow '%s'.",
                            requestedFlow.getSourcePort(),
                            requestedFlow.getSourceSwitch(),
                            conflictedFlow.get().getFlowId()));
        }

        // Check the destination
        Set<Flow> conflictsOnDest;
        if (requestedFlow.getDestinationVlan() == 0) {
            conflictsOnDest = flowCache.getFlowsForEndpoint(
                    requestedFlow.getDestinationSwitch(),
                    requestedFlow.getDestinationPort());
        } else {
            conflictsOnDest = flowCache.getFlowsForEndpoint(
                    requestedFlow.getDestinationSwitch(),
                    requestedFlow.getDestinationPort(),
                    requestedFlow.getDestinationVlan());
        }

        conflictedFlow = conflictsOnDest.stream()
                .filter(flow -> !flow.getFlowId().equals(requestedFlow.getFlowId()))
                .findAny();
        if (conflictedFlow.isPresent()) {
            throw new FlowValidationException(
                    format("The port %d on the switch '%s' has already occupied by the flow '%s'.",
                            requestedFlow.getDestinationPort(),
                            requestedFlow.getDestinationSwitch(),
                            conflictedFlow.get().getFlowId()));
        }
    }
}
