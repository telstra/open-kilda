/* Copyright 2017 Telstra Open Source
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
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForIslRequest;
import org.openkilda.messaging.nbtopology.request.RerouteFlowsForIslRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FlowOperationsBolt extends PersistenceOperationsBolt {
    private transient FlowOperationsService flowOperationsService;

    public FlowOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        this.flowOperationsService = new FlowOperationsService(repositoryFactory);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request, String correlationId) throws IslNotFoundException {
        List<? extends InfoData> result = null;
        if (request instanceof GetFlowsForIslRequest) {
            result = processGetFlowsForLinkRequest((GetFlowsForIslRequest) request);
        } else if (request instanceof RerouteFlowsForIslRequest) {
            result = processRerouteFlowsForLinkRequest((RerouteFlowsForIslRequest) request, tuple);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<FlowResponse> processGetFlowsForLinkRequest(GetFlowsForIslRequest request)
            throws IslNotFoundException {
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();

        return flowOperationsService.getFlowIdsForLink(srcSwitch, srcPort, dstSwitch, dstPort).stream()
                .map(FlowPair::getForward)
                .map(FlowMapper.INSTANCE::map)
                .map(FlowResponse::new)
                .collect(Collectors.toList());
    }

    private List<FlowsResponse> processRerouteFlowsForLinkRequest(RerouteFlowsForIslRequest message, Tuple tuple)
            throws IslNotFoundException {
        SwitchId srcSwitch = message.getSource().getDatapath();
        Integer srcPort = message.getSource().getPortNumber();
        SwitchId dstSwitch = message.getDestination().getDatapath();
        Integer dstPort = message.getDestination().getPortNumber();

        List<String> flowIds = flowOperationsService.getFlowIdsForLink(srcSwitch, srcPort, dstSwitch, dstPort).stream()
                .map(FlowPair::getForward)
                .map(Flow::getFlowId)
                .collect(Collectors.toList());

        flowIds.forEach(flowId -> {
            FlowRerouteRequest request = new FlowRerouteRequest(flowId);
            getOutput().emit(StreamType.REROUTE.toString(), tuple, new Values(request, message.getCorrelationId()));
        });

        return Collections.singletonList(new FlowsResponse(flowIds));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("response", "correlationId"));
        declarer.declareStream(StreamType.REROUTE.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }
}
