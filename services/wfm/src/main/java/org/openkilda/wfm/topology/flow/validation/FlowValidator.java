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
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code FlowValidator} performs checks against the flow validation rules.
 */
public class FlowValidator {

    private final FlowPairRepository flowPairRepository;

    private final SwitchRepository switchRepository;

    public FlowValidator(RepositoryFactory repositoryFactory) {
        this.flowPairRepository = repositoryFactory.createFlowPairRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validate(UnidirectionalFlow flow) throws FlowValidationException, SwitchValidationException {
        checkBandwidth(flow);
        checkFlowForEndpointConflicts(flow);
        checkOneSwitchFlowHasNoConflicts(flow);
        checkSwitchesExists(flow);
    }

    @VisibleForTesting
    void checkBandwidth(UnidirectionalFlow flow) throws FlowValidationException {
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
    void checkFlowForEndpointConflicts(UnidirectionalFlow requestedFlow) throws FlowValidationException {
        // Check the source
        Collection<FlowPair> conflictsOnSource;
        conflictsOnSource = flowPairRepository.findByEndpoint(requestedFlow.getSrcSwitch().getSwitchId(),
                requestedFlow.getSrcPort());

        Optional<String> conflictedFlow = conflictsOnSource.stream()
                .flatMap(flowPair -> Stream.of(flowPair.getForward(), flowPair.getReverse()))
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> (flow.getSrcSwitch().getSwitchId().equals(requestedFlow.getSrcSwitch().getSwitchId())
                        && flow.getSrcPort() == requestedFlow.getSrcPort()
                        && (flow.getSrcVlan() == requestedFlow.getSrcVlan() || flow.getSrcVlan() == 0
                        || requestedFlow.getSrcVlan() == 0))
                        || (flow.getDestSwitch().getSwitchId().equals(requestedFlow.getSrcSwitch().getSwitchId())
                        && flow.getDestPort() == requestedFlow.getSrcPort()
                        && (flow.getDestVlan() == requestedFlow.getSrcVlan() || flow.getDestVlan() == 0
                        || requestedFlow.getSrcVlan() == 0)))
                .map(UnidirectionalFlow::getFlowId)
                .findAny();
        if (conflictedFlow.isPresent()) {
            throw new FlowValidationException(
                    format("The port %d on the switch '%s' has already occupied by the flow '%s'.",
                            requestedFlow.getSrcPort(),
                            requestedFlow.getSrcSwitch().getSwitchId(),
                            conflictedFlow.get()),
                    ErrorType.ALREADY_EXISTS);
        }

        // Check the destination
        Collection<FlowPair> conflictsOnDest;
        conflictsOnDest = flowPairRepository.findByEndpoint(
                requestedFlow.getDestSwitch().getSwitchId(),
                requestedFlow.getDestPort());


        conflictedFlow = conflictsOnDest.stream()
                .flatMap(flowPair -> Stream.of(flowPair.getForward(), flowPair.getReverse()))
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> (flow.getSrcSwitch().getSwitchId().equals(requestedFlow.getDestSwitch().getSwitchId())
                        && flow.getSrcPort() == requestedFlow.getDestPort()
                        && (flow.getSrcVlan() == requestedFlow.getDestVlan() || flow.getSrcVlan() == 0
                        || requestedFlow.getDestVlan() == 0))
                        || (flow.getDestSwitch().getSwitchId().equals(requestedFlow.getDestSwitch().getSwitchId())
                        && flow.getDestPort() == requestedFlow.getDestPort()
                        && (flow.getDestVlan() == requestedFlow.getDestVlan() || flow.getDestVlan() == 0
                        || requestedFlow.getDestVlan() == 0)))
                .map(UnidirectionalFlow::getFlowId)
                .findAny();
        if (conflictedFlow.isPresent()) {
            throw new FlowValidationException(
                    format("The port %d on the switch '%s' has already occupied by the flow '%s'.",
                            requestedFlow.getDestPort(),
                            requestedFlow.getDestSwitch().getSwitchId(),
                            conflictedFlow.get()),
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
    void checkSwitchesExists(UnidirectionalFlow requestedFlow) throws SwitchValidationException {
        final SwitchId sourceId = requestedFlow.getSrcSwitch().getSwitchId();
        final SwitchId destinationId = requestedFlow.getDestSwitch().getSwitchId();

        boolean source;
        boolean destination;

        if (Objects.equals(sourceId, destinationId)) {
            source = destination = switchRepository.exists(sourceId);
        } else {
            source = switchRepository.exists(sourceId);
            destination = switchRepository.exists(destinationId);
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

    /**
     * Ensure vlans are not equal in the case when there is an attempt to create one-switch flow for a single port.
     */
    @VisibleForTesting
    void checkOneSwitchFlowHasNoConflicts(UnidirectionalFlow requestedFlow) throws SwitchValidationException {
        if (requestedFlow.getSrcSwitch().equals(requestedFlow.getDestSwitch())
                && requestedFlow.getSrcPort() == requestedFlow.getDestPort()
                && requestedFlow.getSrcVlan() == requestedFlow.getDestVlan()) {

            throw new SwitchValidationException(
                    "It is not allowed to create one-switch flow for the same ports and vlans");
        }
    }
}
