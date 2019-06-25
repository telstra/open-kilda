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
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.FlowPatchRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForIslRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForSwitchRequest;
import org.openkilda.messaging.nbtopology.request.RerouteFlowsForIslRequest;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FlowOperationsBolt extends PersistenceOperationsBolt {
    private transient FlowOperationsService flowOperationsService;
    private transient FeatureTogglesRepository featureTogglesRepository;

    public FlowOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        this.flowOperationsService = new FlowOperationsService(repositoryFactory, transactionManager);
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) {
        List<? extends InfoData> result = null;
        if (request instanceof GetFlowsForIslRequest) {
            result = processGetFlowsForLinkRequest((GetFlowsForIslRequest) request);
        } else if (request instanceof GetFlowsForSwitchRequest) {
            result = processGetFlowsForSwitchRequest((GetFlowsForSwitchRequest) request);
        } else if (request instanceof RerouteFlowsForIslRequest) {
            result = processRerouteFlowsForLinkRequest((RerouteFlowsForIslRequest) request, tuple);
        } else if (request instanceof GetFlowPathRequest) {
            result = processGetFlowPathRequest((GetFlowPathRequest) request);
        } else if (request instanceof FlowPatchRequest) {
            result = processFlowPatchRequest((FlowPatchRequest) request);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<FlowResponse> processGetFlowsForLinkRequest(GetFlowsForIslRequest request) {
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();

        try {
            return flowOperationsService.getFlowPathsForLink(srcSwitch, srcPort, dstSwitch, dstPort).stream()
                    .map(FlowPath::getFlow)
                    .distinct()
                    .map(FlowMapper.INSTANCE::map)
                    .map(FlowResponse::new)
                    .collect(Collectors.toList());
        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        }
    }

    private List<FlowResponse> processGetFlowsForSwitchRequest(GetFlowsForSwitchRequest request) {
        SwitchId srcSwitch = request.getSwitchId();
        Integer srcPort = request.getPort();

        try {
            return flowOperationsService.getFlowPathsForEndpoint(srcSwitch, srcPort).stream()
                    .map(FlowPath::getFlow)
                    .distinct()
                    .map(FlowMapper.INSTANCE::map)
                    .map(FlowResponse::new)
                    .collect(Collectors.toList());
        } catch (SwitchNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Switch was not found.");
        }
    }

    private List<FlowsResponse> processRerouteFlowsForLinkRequest(RerouteFlowsForIslRequest message, Tuple tuple) {
        SwitchId srcSwitch = message.getSource().getDatapath();
        Integer srcPort = message.getSource().getPortNumber();
        SwitchId dstSwitch = message.getDestination().getDatapath();
        Integer dstPort = message.getDestination().getPortNumber();

        Collection<FlowPath> paths;
        try {
            paths = flowOperationsService.getFlowPathsForLink(srcSwitch, srcPort, dstSwitch, dstPort);
        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        }

        boolean flowsRerouteViaFlowHs = featureTogglesRepository.find()
                .map(FeatureToggles::getFlowsRerouteViaFlowHs)
                .orElse(FeatureToggles.DEFAULTS.getFlowsRerouteViaFlowHs());

        flowOperationsService.groupFlowIdWithPathIdsForRerouting(paths)
                .forEach((flowId, pathIds) -> {
                    FlowRerouteRequest rerouteRequest = new FlowRerouteRequest(flowId, false, pathIds);
                    getOutput().emit(
                            flowsRerouteViaFlowHs ? StreamType.FLOWHS.toString() : StreamType.REROUTE.toString(),
                            tuple, new Values(rerouteRequest, message.getCorrelationId()));
                });

        List<String> flowIds = paths.stream()
                .map(FlowPath::getFlow)
                .map(Flow::getFlowId)
                .distinct()
                .collect(Collectors.toList());
        return Collections.singletonList(new FlowsResponse(flowIds));
    }

    private List<GetFlowPathResponse> processGetFlowPathRequest(GetFlowPathRequest request) {
        final String errorDescription = "Could not get flow path";

        try {
            return flowOperationsService.getFlowPath(request.getFlowId())
                    .stream()
                    .map(GetFlowPathResponse::new)
                    .collect(Collectors.toList());
        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), errorDescription);
        } catch (Exception e) {
            throw new MessageException(ErrorType.INTERNAL_ERROR, e.getMessage(), errorDescription);
        }
    }

    private List<FlowResponse> processFlowPatchRequest(FlowPatchRequest request) {
        FlowDto flowDto = request.getFlow();

        try {
            UnidirectionalFlow flow = flowOperationsService.updateFlow(flowDto);
            return Collections.singletonList(new FlowResponse(FlowMapper.INSTANCE.map(flow)));

        } catch (FlowNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "Flow was not found.");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(StreamType.REROUTE.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.FLOWHS.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }
}
