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

package org.openkilda.wfm.topology.flowhs.bolts;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This implementation of the class is temporary.
 * It only works with DB. Switch rules wouldn't be modified.
 * Class is just a stub to give an API for users. It will be modified later.
 */
public class HaFlowUpdateHubBolt extends AbstractBolt {
    public static final String COMMON_ERROR_MESSAGE = "Couldn't update HA-flow";
    private transient HaFlowRepository haFlowRepository;
    private transient SwitchRepository switchRepository;

    public HaFlowUpdateHubBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void init() {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String key = pullValue(input, MessageKafkaTranslator.FIELD_ID_KEY, String.class);
        CommandData payload = pullValue(input, FIELD_ID_PAYLOAD, CommandData.class);

        try {
            if (payload instanceof HaFlowRequest) {
                handleHaFlowUpdate(key, (HaFlowRequest) payload);
            } else if (payload instanceof HaFlowPartialUpdateRequest) {
                handleHaFlowPartialUpdate(key, (HaFlowPartialUpdateRequest) payload);
            } else {
                unhandledInput(input);
            }
        } catch (FlowProcessingException e) {
            emitErrorMessage(e.getErrorType(), COMMON_ERROR_MESSAGE, e.getMessage());
        } catch (SwitchNotFoundException e) {
            emitErrorMessage(ErrorType.NOT_FOUND, COMMON_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            emitErrorMessage(ErrorType.INTERNAL_ERROR, COMMON_ERROR_MESSAGE, e.getMessage());
        }
    }

    private void handleHaFlowUpdate(String key, HaFlowRequest payload) throws SwitchNotFoundException {
        HaFlow returnHaFlow = persistenceManager.getTransactionManager().doInTransaction(() -> {
            Optional<HaFlow> foundHaFlow = haFlowRepository.findById(payload.getHaFlowId());
            if (!foundHaFlow.isPresent()) {
                throw new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("HA-flow %s not found.", payload.getHaFlowId()));
            }
            HaFlow haFlow = foundHaFlow.get();
            Map<String, HaSubFlow> subFlowMap = haFlow.getHaSubFlows().stream()
                    .collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, Function.identity()));

            for (HaSubFlowDto subFlowPayload : payload.getSubFlows()) {
                HaSubFlow subFlow = subFlowMap.get(subFlowPayload.getFlowId());
                if (subFlow == null) {
                    throw new FlowProcessingException(ErrorType.DATA_INVALID,
                            format("HA-flow %s has no sub flow %s",
                                    payload.getHaFlowId(), subFlowPayload.getFlowId()));
                }
                updateSubFlow(subFlow, subFlowPayload);
            }
            updateHaFlow(haFlow, payload);
            return haFlow;
        });
        sendHaFlowToNorthBound(key, new HaFlowResponse(HaFlowMapper.INSTANCE.toHaFlowDto(returnHaFlow)));
    }

    private void handleHaFlowPartialUpdate(String key, HaFlowPartialUpdateRequest payload)
            throws SwitchNotFoundException {
        HaFlow returnHaFlow = persistenceManager.getTransactionManager().doInTransaction(() -> {
            Optional<HaFlow> foundHaFlow = haFlowRepository.findById(payload.getHaFlowId());
            if (!foundHaFlow.isPresent()) {
                throw new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("HA-flow %s not found.", payload.getHaFlowId()));
            }
            HaFlow haFlow = foundHaFlow.get();
            if (payload.getSubFlows() != null) {
                updateSubFlows(payload, haFlow);
            }
            updateHaFlow(haFlow, payload);
            return haFlow;
        });
        sendHaFlowToNorthBound(key, new HaFlowResponse(HaFlowMapper.INSTANCE.toHaFlowDto(returnHaFlow)));
    }

    private void updateSubFlows(HaFlowPartialUpdateRequest payload, HaFlow haFlow) throws SwitchNotFoundException {
        Map<String, HaSubFlow> subFlowMap = haFlow.getHaSubFlows().stream()
                .collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, Function.identity()));

        for (HaSubFlowPartialUpdateDto subFlowPayload : payload.getSubFlows()) {
            HaSubFlow subFlow = subFlowMap.get(subFlowPayload.getFlowId());
            if (subFlow == null) {
                throw new FlowProcessingException(ErrorType.DATA_INVALID,
                        format("HA-flow %s has no sub flow %s",
                                payload.getHaFlowId(), subFlowPayload.getFlowId()));
            }
            updateSubFlow(subFlow, subFlowPayload);
        }
    }

    private void updateHaFlow(HaFlow target, HaFlowRequest source) throws SwitchNotFoundException {
        if (source.getSharedEndpoint() != null) {
            target.setSharedSwitch(getSwitch(source.getSharedEndpoint().getSwitchId()));
            target.setSharedPort(source.getSharedEndpoint().getPortNumber());
            target.setSharedOuterVlan(source.getSharedEndpoint().getOuterVlanId());
            target.setSharedInnerVlan(source.getSharedEndpoint().getInnerVlanId());
        }
        target.setMaximumBandwidth(source.getMaximumBandwidth());
        target.setPathComputationStrategy(source.getPathComputationStrategy());
        target.setEncapsulationType(source.getEncapsulationType());
        target.setMaxLatency(source.getMaxLatency());
        target.setMaxLatencyTier2(source.getMaxLatencyTier2());
        target.setIgnoreBandwidth(source.isIgnoreBandwidth());
        target.setPeriodicPings(source.isPeriodicPings());
        target.setPinned(source.isPinned());
        target.setPriority(source.getPriority());
        target.setStrictBandwidth(source.isStrictBandwidth());
        target.setDescription(source.getDescription());
        target.setAllocateProtectedPath(source.isAllocateProtectedPath());
    }

    private void updateHaFlow(HaFlow target, HaFlowPartialUpdateRequest source) throws SwitchNotFoundException {
        updateSharedEndpoint(target, source.getSharedEndpoint());
        Optional.ofNullable(source.getMaximumBandwidth()).ifPresent(target::setMaximumBandwidth);
        Optional.ofNullable(source.getPathComputationStrategy()).ifPresent(target::setPathComputationStrategy);
        Optional.ofNullable(source.getEncapsulationType()).ifPresent(target::setEncapsulationType);
        Optional.ofNullable(source.getMaxLatency()).ifPresent(target::setMaxLatency);
        Optional.ofNullable(source.getMaxLatencyTier2()).ifPresent(target::setMaxLatencyTier2);
        Optional.ofNullable(source.getIgnoreBandwidth()).ifPresent(target::setIgnoreBandwidth);
        Optional.ofNullable(source.getPeriodicPings()).ifPresent(target::setPeriodicPings);
        Optional.ofNullable(source.getPinned()).ifPresent(target::setPinned);
        Optional.ofNullable(source.getPriority()).ifPresent(target::setPriority);
        Optional.ofNullable(source.getStrictBandwidth()).ifPresent(target::setStrictBandwidth);
        Optional.ofNullable(source.getDescription()).ifPresent(target::setDescription);
        Optional.ofNullable(source.getAllocateProtectedPath()).ifPresent(target::setAllocateProtectedPath);
    }

    private void updateSharedEndpoint(HaFlow targetHaFlow, FlowPartialUpdateEndpoint source)
            throws SwitchNotFoundException {
        if (source == null) {
            return;
        }
        if (source.getSwitchId() != null) {
            targetHaFlow.setSharedSwitch(getSwitch(source.getSwitchId()));
        }
        Optional.ofNullable(source.getPortNumber()).ifPresent(targetHaFlow::setSharedPort);
        Optional.ofNullable(source.getVlanId()).ifPresent(targetHaFlow::setSharedOuterVlan);
        Optional.ofNullable(source.getInnerVlanId()).ifPresent(targetHaFlow::setSharedInnerVlan);
    }

    private void updateSubFlow(HaSubFlow target, HaSubFlowDto source) throws SwitchNotFoundException {
        target.setDescription(source.getDescription());
        target.setEndpointSwitch(getSwitch(source.getEndpoint().getSwitchId()));
        target.setEndpointPort(source.getEndpoint().getPortNumber());
        target.setEndpointVlan(source.getEndpoint().getOuterVlanId());
        target.setEndpointInnerVlan(source.getEndpoint().getInnerVlanId());
        target.setStatus(FlowStatus.UP);
    }

    private void updateSubFlow(HaSubFlow target, HaSubFlowPartialUpdateDto source) throws SwitchNotFoundException {
        if (source.getEndpoint().getSwitchId() != null) {
            target.setEndpointSwitch(getSwitch(source.getEndpoint().getSwitchId()));
        }
        Optional.ofNullable(source.getDescription()).ifPresent(target::setDescription);
        Optional.ofNullable(source.getEndpoint().getPortNumber()).ifPresent(target::setEndpointPort);
        Optional.ofNullable(source.getEndpoint().getVlanId()).ifPresent(target::setEndpointVlan);
        Optional.ofNullable(source.getEndpoint().getInnerVlanId()).ifPresent(target::setEndpointInnerVlan);
    }

    private void sendHaFlowToNorthBound(String key, InfoData response) {
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(),
                getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(key, message));
    }

    private void emitErrorMessage(ErrorType type, String message, String description) {
        String requestId = getCommandContext().getCorrelationId();
        ErrorData errorData = new ErrorData(type, message, description);
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(requestId,
                new ErrorMessage(errorData, System.currentTimeMillis(), requestId)));
    }

    private Switch getSwitch(SwitchId switchId) throws SwitchNotFoundException {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }
}
