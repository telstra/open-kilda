/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.DeactivateSwitchInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.DeleteSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.nbtopology.request.UpdateSwitchUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.DeleteSwitchResponse;
import org.openkilda.messaging.nbtopology.response.GetSwitchResponse;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.IllegalSwitchStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;
import org.openkilda.wfm.topology.nbworker.services.SwitchOperationsService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collections;
import java.util.List;

public class SwitchOperationsBolt extends PersistenceOperationsBolt {
    private transient SwitchOperationsService switchOperationsService;
    private transient FlowOperationsService flowOperationsService;

    private transient FeatureTogglesRepository featureTogglesRepository;

    public SwitchOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        this.switchOperationsService =
                new SwitchOperationsService(repositoryFactory, transactionManager);
        this.flowOperationsService = new FlowOperationsService(repositoryFactory, transactionManager);

        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) {
        List<? extends InfoData> result = null;
        if (request instanceof GetSwitchesRequest) {
            result = getSwitches();
        } else if (request instanceof UpdateSwitchUnderMaintenanceRequest) {
            result = updateSwitchUnderMaintenanceFlag((UpdateSwitchUnderMaintenanceRequest) request, tuple);
        } else if (request instanceof GetSwitchRequest) {
            result = getSwitch((GetSwitchRequest) request);
        } else if (request instanceof DeleteSwitchRequest) {
            result = Collections.singletonList(deleteSwitch((DeleteSwitchRequest) request));
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<GetSwitchResponse> getSwitches() {
        return switchOperationsService.getAllSwitches();
    }

    private List<GetSwitchResponse> getSwitch(GetSwitchRequest request) {
        SwitchId switchId = request.getSwitchId();

        try {
            return Collections.singletonList(switchOperationsService.getSwitch(switchId));
        } catch (SwitchNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Switch was not found.");
        }
    }

    private List<GetSwitchResponse> updateSwitchUnderMaintenanceFlag(UpdateSwitchUnderMaintenanceRequest request,
                                                                     Tuple tuple) {
        SwitchId switchId = request.getSwitchId();
        boolean underMaintenance = request.isUnderMaintenance();
        boolean evacuate = request.isEvacuate();

        Switch sw;
        try {
            sw = switchOperationsService.updateSwitchUnderMaintenanceFlag(switchId, underMaintenance);
        } catch (SwitchNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Switch was not found.");
        }

        if (underMaintenance && evacuate) {
            boolean flowsRerouteViaFlowHs = featureTogglesRepository.find()
                    .map(FeatureToggles::getFlowsRerouteViaFlowHs)
                    .orElse(FeatureToggles.DEFAULTS.getFlowsRerouteViaFlowHs());

            flowOperationsService.groupFlowIdWithPathIdsForRerouting(
                    flowOperationsService.getFlowPathsForSwitch(switchId)
            ).forEach((flowId, pathIds) -> {
                FlowRerouteRequest rerouteRequest = new FlowRerouteRequest(flowId, false, pathIds);
                CommandContext forkedContext = getCommandContext().fork(flowId);
                getOutput().emit(
                        flowsRerouteViaFlowHs ? StreamType.FLOWHS.toString() : StreamType.REROUTE.toString(),
                        tuple, new Values(rerouteRequest, forkedContext.getCorrelationId()));
            });
        }

        return Collections.singletonList(new GetSwitchResponse(sw));
    }

    private DeleteSwitchResponse deleteSwitch(DeleteSwitchRequest request) {
        SwitchId switchId = request.getSwitchId();
        boolean force = request.isForce();
        boolean deleted = transactionManager.doInTransaction(() -> {
            try {
                if (!force) {
                    switchOperationsService.checkSwitchIsDeactivated(switchId);
                    switchOperationsService.checkSwitchHasNoFlows(switchId);
                    switchOperationsService.checkSwitchHasNoFlowSegments(switchId);
                    switchOperationsService.checkSwitchHasNoIsls(switchId);
                }
                return switchOperationsService.deleteSwitch(switchId, force);
            } catch (SwitchNotFoundException e) {
                String message = String.format("Could not delete switch '%s': '%s'", switchId, e.getMessage());
                log.error(message);
                throw new MessageException(ErrorType.NOT_FOUND, message, "Switch is not found.");
            } catch (IllegalSwitchStateException e) {
                String message = String.format("Could not delete switch '%s': '%s'", switchId, e.getMessage());
                log.error(message);
                throw new MessageException(ErrorType.REQUEST_INVALID, message, "Switch is in illegal state");
            }
        });

        if (deleted) {
            DeactivateSwitchInfoData data = new DeactivateSwitchInfoData(switchId);
            getOutput().emit(StreamType.DISCO.toString(), getCurrentTuple(), new Values(data, getCorrelationId()));
        }

        log.info("{} deletion of switch '{}'", deleted ? "Successful" : "Unsuccessful", switchId);
        return new DeleteSwitchResponse(deleted);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(StreamType.REROUTE.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.FLOWHS.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.DISCO.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }
}
