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

import com.google.common.annotations.VisibleForTesting;
import org.openkilda.messaging.model.Flow;
import org.openkilda.pce.cache.FlowCache;

import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;

/**
 * {@code FlowValidator} performs checks against the flow validation rules.
 */
public class FlowValidator {

    private final FlowCache flowCache;

    public FlowValidator(FlowCache flowCache) {
        this.flowCache = flowCache;
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validate(Flow flow) throws FlowValidationException {
        checkBandwidth(flow);
        checkFlowForEndpointConflicts(flow);
    }

    @VisibleForTesting
    void checkBandwidth(Flow flow) throws FlowValidationException {
        if (flow.getBandwidth() < 0) {
            throw new FlowValidationException(
                    format("The flow '%s' has invalid bandwidth %d provided.",
                            flow.getFlowId(),
                            flow.getBandwidth()));
        }
    }

    /**
     * Checks a flow for endpoints' conflicts.
     *
     * @param requestedFlow a flow to be validated.
     * @throws FlowValidationException is thrown in a case when flow endpoints conflict with existing flows.
     */
    @VisibleForTesting
    void checkFlowForEndpointConflicts(Flow requestedFlow) throws FlowValidationException {
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
