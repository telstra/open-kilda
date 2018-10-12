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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.cache.FlowCache;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code FlowValidator} performs checks against the flow validation rules.
 */
public class FlowValidator {

    private final FlowCache flowCache;

    private SwitchRepository switchRepository;

    public FlowValidator(FlowCache flowCache, SwitchRepository switchRepository) {
        this.flowCache = flowCache;
        this.switchRepository = switchRepository;
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validate(Flow flow) throws FlowValidationException, SwitchValidationException {
        checkBandwidth(flow);
        checkFlowForEndpointConflicts(flow);
        checkSwitchesExists(flow);
    }

    @VisibleForTesting
    void checkBandwidth(Flow flow) throws FlowValidationException {
        if (flow.getBandwidth() < 0) {
            throw new FlowValidationException(
                    format("The flow '%s' has invalid bandwidth %d provided.",
                            flow.getFlowId(),
                            flow.getBandwidth()),
                    ErrorType.DATA_INVALID);
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
                            conflictedFlow.get().getFlowId()),
                    ErrorType.ALREADY_EXISTS);
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
                            conflictedFlow.get().getFlowId()),
                    ErrorType.ALREADY_EXISTS);
        }
    }

    /**
     * Ensure switches are exists.
     *
     * @param requestedFlow a flow to be validated.
     * @throws SwitchValidationException if switch not found.
     */
    @VisibleForTesting
    void checkSwitchesExists(Flow requestedFlow) throws SwitchValidationException {
        final SwitchId sourceId = requestedFlow.getSourceSwitch();
        final SwitchId destinationId = requestedFlow.getDestinationSwitch();

        boolean source;
        boolean destination;

        if (Objects.equals(sourceId, destinationId)) {
            source = destination =
                    switchRepository.findBySwitchId(new org.openkilda.model.SwitchId(sourceId.toString())) != null;
        } else {
            source =
                    switchRepository.findBySwitchId(new org.openkilda.model.SwitchId(sourceId.toString())) != null;
            destination =
                    switchRepository.findBySwitchId(new org.openkilda.model.SwitchId(destinationId.toString())) != null;
        }

        if (!source && !destination) {
            throw new SwitchValidationException(
                    String.format("Source switch %s and Destination switch %s are not connected to the controller",
                            sourceId, destinationId));
        } else if (!source) {
            throw new SwitchValidationException(
                    String.format("Source switch %s is not connected to the controller", sourceId));
        } else if (!destination) {
            throw new SwitchValidationException(
                    String.format("Destination switch %s is not connected to the controller", destinationId));
        }
    }
}
