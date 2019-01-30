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
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.messaging.nbtopology.request.UpdateSwitchUnderMaintenanceRequest;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
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

    private int islCostWhenUnderMaintenance;

    public SwitchOperationsBolt(PersistenceManager persistenceManager, int islCostWhenUnderMaintenance) {
        super(persistenceManager);
        this.islCostWhenUnderMaintenance = islCostWhenUnderMaintenance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        this.switchOperationsService =
                new SwitchOperationsService(repositoryFactory, transactionManager, islCostWhenUnderMaintenance);
        this.flowOperationsService = new FlowOperationsService(repositoryFactory);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request, String correlationId) {
        List<? extends InfoData> result = null;
        if (request instanceof GetSwitchesRequest) {
            result = getSwitches();
        } else if (request instanceof UpdateSwitchUnderMaintenanceRequest) {
            result = updateSwitchUnderMaintenanceFlag((UpdateSwitchUnderMaintenanceRequest) request,
                    tuple, correlationId);
        } else if (request instanceof GetSwitchRequest) {
            result = getSwitch((GetSwitchRequest) request);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<SwitchInfoData> getSwitches() {
        return switchOperationsService.getAllSwitches();
    }

    private List<SwitchInfoData> getSwitch(GetSwitchRequest request) {
        SwitchId switchId = request.getSwitchId();

        try {
            return Collections.singletonList(SwitchMapper.INSTANCE.map(switchOperationsService.getSwitch(switchId)));
        } catch (SwitchNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Switch was not found.");
        }
    }

    private List<SwitchInfoData> updateSwitchUnderMaintenanceFlag(UpdateSwitchUnderMaintenanceRequest request,
                                                                  Tuple tuple,
                                                                  String correlationId) {
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
            flowOperationsService.getFlowIdsForSwitch(switchId).forEach(flowId -> {
                FlowRerouteRequest rerouteRequest = new FlowRerouteRequest(flowId);
                getOutput().emit(StreamType.REROUTE.toString(), tuple, new Values(rerouteRequest, correlationId));
            });
        }

        return Collections.singletonList(SwitchMapper.INSTANCE.map(sw));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("response", "correlationId"));
        declarer.declareStream(StreamType.REROUTE.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }
}
