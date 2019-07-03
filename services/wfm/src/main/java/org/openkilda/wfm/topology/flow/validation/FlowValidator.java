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
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code FlowValidator} performs checks against the flow validation rules.
 */
public class FlowValidator {

    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;

    public FlowValidator(RepositoryFactory repositoryFactory) {
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.islRepository = repositoryFactory.createIslRepository();
    }

    /**
     * Validates the specified flow.
     *
     * @param flow a flow to be validated.
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validate(Flow flow) throws FlowValidationException, SwitchValidationException {
        checkBandwidth(flow);
        checkFlowForIslConflicts(flow);
        checkFlowForEndpointConflicts(flow);
        checkOneSwitchFlowHasNoConflicts(flow);
        checkSwitchesExists(flow);
    }

    /**
     * Validates flows for swap.
     * @param firstFlow a first flow.
     * @param secondFlow a second flow.
     * @throws FlowValidationException is thrown if a violation is found.
     */
    public void validateFowSwap(Flow firstFlow, Flow secondFlow) throws FlowValidationException {
        checkFlowForIslConflicts(firstFlow);
        checkFlowForIslConflicts(secondFlow);
        checkFlowForEndpointConflicts(firstFlow, secondFlow.getFlowId());
        checkFlowForEndpointConflicts(secondFlow, firstFlow.getFlowId());
        checkForEqualsEndpoints(firstFlow, secondFlow);
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
     * Checks a flow for conflicts with ISL ports.
     *
     * @param requestedFlow a flow to be validated.
     * @throws FlowValidationException is thrown in a case when flow endpoints conflict with existing ISL ports.
     */
    @VisibleForTesting
    void checkFlowForIslConflicts(Flow requestedFlow) throws FlowValidationException {
        // Check the source
        if (!islRepository.findByEndpoint(requestedFlow.getSrcSwitch().getSwitchId(),
                requestedFlow.getSrcPort()).isEmpty()) {
            String errorMessage = format("The port %d on the switch '%s' is occupied by an ISL.",
                    requestedFlow.getSrcPort(), requestedFlow.getSrcSwitch().getSwitchId());
            throw new FlowValidationException(errorMessage, ErrorType.PARAMETERS_INVALID);
        }

        // Check the destination
        if (!islRepository.findByEndpoint(requestedFlow.getDestSwitch().getSwitchId(),
                requestedFlow.getDestPort()).isEmpty()) {
            String errorMessage = format("The port %d on the switch '%s' is occupied by an ISL.",
                    requestedFlow.getDestPort(), requestedFlow.getDestSwitch().getSwitchId());
            throw new FlowValidationException(errorMessage, ErrorType.PARAMETERS_INVALID);
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
        Collection<Flow> conflictsOnSource = flowRepository.findByEndpoint(requestedFlow.getSrcSwitch().getSwitchId(),
                requestedFlow.getSrcPort());

        Optional<Flow> conflictSrcSrc = conflictsOnSource.stream()
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> flow.getSrcSwitch().getSwitchId().equals(requestedFlow.getSrcSwitch().getSwitchId())
                        && flow.getSrcPort() == requestedFlow.getSrcPort()
                        && (flow.getSrcVlan() == requestedFlow.getSrcVlan()))
                .findAny();

        if (conflictSrcSrc.isPresent()) {
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' source: switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' source: switch=%s port=%d vlan=%d",
                    requestedFlow.getFlowId(), conflictSrcSrc.get().getFlowId(),
                    requestedFlow.getFlowId(), requestedFlow.getSrcSwitch().getSwitchId().toString(),
                    requestedFlow.getSrcPort(), requestedFlow.getSrcVlan(),
                    conflictSrcSrc.get().getFlowId(),
                    conflictSrcSrc.get().getSrcSwitch().getSwitchId().toString(),
                    conflictSrcSrc.get().getSrcPort(), conflictSrcSrc.get().getSrcVlan());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        Optional<Flow> conflictDstSrc = conflictsOnSource.stream()
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> flow.getDestSwitch().getSwitchId().equals(requestedFlow.getSrcSwitch().getSwitchId())
                        && flow.getDestPort() == requestedFlow.getSrcPort()
                        && (flow.getDestVlan() == requestedFlow.getSrcVlan()))
                .findAny();

        if (conflictDstSrc.isPresent()) {
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' source: switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' destination: switch=%s port=%d vlan=%d",
                    requestedFlow.getFlowId(), conflictDstSrc.get().getFlowId(),
                    requestedFlow.getFlowId(), requestedFlow.getSrcSwitch().getSwitchId().toString(),
                    requestedFlow.getSrcPort(), requestedFlow.getSrcVlan(),
                    conflictDstSrc.get().getFlowId(),
                    conflictDstSrc.get().getDestSwitch().getSwitchId().toString(),
                    conflictDstSrc.get().getDestPort(), conflictDstSrc.get().getDestVlan());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        // Check the destination
        Collection<Flow> conflictsOnDest = flowRepository.findByEndpoint(
                requestedFlow.getDestSwitch().getSwitchId(),
                requestedFlow.getDestPort());


        Optional<Flow> conflictSrcDst = conflictsOnDest.stream()
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> flow.getSrcSwitch().getSwitchId().equals(requestedFlow.getDestSwitch().getSwitchId())
                        && flow.getSrcPort() == requestedFlow.getDestPort()
                        && (flow.getSrcVlan() == requestedFlow.getDestVlan()))
                .findAny();

        if (conflictSrcDst.isPresent()) {
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' destination: switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' source: switch=%s port=%d vlan=%d",
                    requestedFlow.getFlowId(), conflictSrcDst.get().getFlowId(),
                    requestedFlow.getFlowId(), requestedFlow.getDestSwitch().getSwitchId().toString(),
                    requestedFlow.getDestPort(), requestedFlow.getDestVlan(),
                    conflictSrcDst.get().getFlowId(),
                    conflictSrcDst.get().getSrcSwitch().getSwitchId().toString(),
                    conflictSrcDst.get().getSrcPort(), conflictSrcDst.get().getSrcVlan());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        Optional<Flow> conflictDstDst = conflictsOnDest.stream()
                .filter(flow -> !requestedFlow.getFlowId().equals(flow.getFlowId()))
                .filter(flow -> flow.getDestSwitch().getSwitchId().equals(requestedFlow.getDestSwitch().getSwitchId())
                        && flow.getDestPort() == requestedFlow.getDestPort()
                        && (flow.getDestVlan() == requestedFlow.getDestVlan()))
                .findAny();

        if (conflictDstDst.isPresent()) {
            String errorMessage = format("Requested flow '%s' conflicts with existing flow '%s'. "
                            + "Details: "
                            + "requested flow '%s' destination: switch=%s port=%d vlan=%d, "
                            + "existing flow '%s' destination: switch=%s port=%d vlan=%d",
                    requestedFlow.getFlowId(), conflictDstDst.get().getFlowId(),
                    requestedFlow.getFlowId(), requestedFlow.getDestSwitch().getSwitchId().toString(),
                    requestedFlow.getDestPort(), requestedFlow.getDestVlan(),
                    conflictDstDst.get().getFlowId(),
                    conflictDstDst.get().getDestSwitch().getSwitchId().toString(),
                    conflictDstDst.get().getDestPort(), conflictDstDst.get().getDestVlan());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }
    }

    @VisibleForTesting
    void checkFlowForEndpointConflicts(Flow checkedFlow, String flowIdToBeSwappedWith)
            throws FlowValidationException {
        Collection<Flow> conflictsOnSource = flowRepository.findByEndpoint(checkedFlow.getSrcSwitch().getSwitchId(),
                checkedFlow.getSrcPort());

        Set<String> processingFlowIds = ImmutableSet.of(checkedFlow.getFlowId(), flowIdToBeSwappedWith);
        Optional<Flow> conflictSrcSrc = conflictsOnSource.stream()
                .filter(flow -> !processingFlowIds.contains(flow.getFlowId()))
                .filter(flow -> flow.getSrcSwitch().getSwitchId().equals(checkedFlow.getSrcSwitch().getSwitchId())
                        && flow.getSrcPort() == checkedFlow.getSrcPort()
                        && (flow.getSrcVlan() == checkedFlow.getSrcVlan()))
                .findAny();

        if (conflictSrcSrc.isPresent()) {
            String errorMessage = format("Requested source endpoint for flow '%s' conflicts with "
                            + "existing source endpoint for flow '%s'.",
                    checkedFlow.getFlowId(), conflictSrcSrc.get().getFlowId());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        Optional<Flow> conflictDstSrc = conflictsOnSource.stream()
                .filter(flow -> !processingFlowIds.contains(flow.getFlowId()))
                .filter(flow -> flow.getDestSwitch().getSwitchId().equals(checkedFlow.getSrcSwitch().getSwitchId())
                        && flow.getDestPort() == checkedFlow.getSrcPort()
                        && (flow.getDestVlan() == checkedFlow.getSrcVlan()))
                .findAny();

        if (conflictDstSrc.isPresent()) {
            String errorMessage = format("Requested source endpoint for flow '%s' conflicts with "
                            + "existing destination endpoint for flow '%s'.",
                    checkedFlow.getFlowId(), conflictDstSrc.get().getFlowId());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        // Check the destination
        Collection<Flow> conflictsOnDest = flowRepository.findByEndpoint(
                checkedFlow.getDestSwitch().getSwitchId(),
                checkedFlow.getDestPort());


        Optional<Flow> conflictSrcDst = conflictsOnDest.stream()
                .filter(flow -> !processingFlowIds.contains(flow.getFlowId()))
                .filter(flow -> flow.getSrcSwitch().getSwitchId().equals(checkedFlow.getDestSwitch().getSwitchId())
                        && flow.getSrcPort() == checkedFlow.getDestPort()
                        && (flow.getSrcVlan() == checkedFlow.getDestVlan()))
                .findAny();

        if (conflictSrcDst.isPresent()) {
            String errorMessage = format("Requested destination endpoint for flow '%s' conflicts with "
                            + "existing source endpoint for flow '%s'.",
                    checkedFlow.getFlowId(), conflictSrcDst.get().getFlowId());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }

        Optional<Flow> conflictDstDst = conflictsOnDest.stream()
                .filter(flow -> !processingFlowIds.contains(flow.getFlowId()))
                .filter(flow -> flow.getDestSwitch().getSwitchId().equals(checkedFlow.getDestSwitch().getSwitchId())
                        && flow.getDestPort() == checkedFlow.getDestPort()
                        && (flow.getDestVlan() == checkedFlow.getDestVlan()))
                .findAny();

        if (conflictDstDst.isPresent()) {
            String errorMessage = format("Requested destination endpoint for flow '%s' conflicts "
                            + "with existing destination endpoint for flow '%s'.",
                    checkedFlow.getFlowId(), conflictDstDst.get().getFlowId());
            throw new FlowValidationException(errorMessage, ErrorType.ALREADY_EXISTS);
        }
    }

    /**
     * Check for equals endpoints.
     *
     * @param firstFlow a first flow.
     * @param secondFlow a second flow.
     */
    void checkForEqualsEndpoints(Flow firstFlow, Flow secondFlow) throws FlowValidationException {
        List<FlowEndpointPayload> endpoints = new ArrayList<>();
        endpoints.add(new FlowEndpointPayload(firstFlow.getSrcSwitch().getSwitchId(),
                firstFlow.getSrcPort(), firstFlow.getSrcVlan()));
        endpoints.add(new FlowEndpointPayload(firstFlow.getDestSwitch().getSwitchId(),
                firstFlow.getDestPort(), firstFlow.getDestVlan()));
        endpoints.add(new FlowEndpointPayload(secondFlow.getSrcSwitch().getSwitchId(),
                secondFlow.getSrcPort(), secondFlow.getSrcVlan()));
        endpoints.add(new FlowEndpointPayload(secondFlow.getDestSwitch().getSwitchId(),
                secondFlow.getDestPort(), secondFlow.getDestVlan()));

        Set<FlowEndpointPayload> checkSet = new HashSet<>();
        for (FlowEndpointPayload endpoint : endpoints) {
            if (!checkSet.contains(endpoint)) {
                checkSet.add(endpoint);
            } else {
                String message = "New requested endpoint for '%s' conflicts with existing endpoint for '%s'";
                if (checkSet.size() <= 1) {
                    message = String.format(message, firstFlow.getFlowId(), firstFlow.getFlowId());
                } else {
                    if (endpoints.indexOf(endpoint) <= 1) {
                        message = String.format(message, secondFlow.getFlowId(), firstFlow.getFlowId());
                    } else {
                        message = String.format(message, secondFlow.getFlowId(), secondFlow.getFlowId());
                    }
                }
                throw new FlowValidationException(message, ErrorType.REQUEST_INVALID);
            }
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
    void checkOneSwitchFlowHasNoConflicts(Flow requestedFlow) throws SwitchValidationException {
        if (requestedFlow.isOneSwitchFlow()
                && requestedFlow.getSrcPort() == requestedFlow.getDestPort()
                && requestedFlow.getSrcVlan() == requestedFlow.getDestVlan()) {

            throw new SwitchValidationException(
                    "It is not allowed to create one-switch flow for the same ports and vlans");
        }
    }
}
