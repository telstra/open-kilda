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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowsForIslRequest;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.List;
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
    public void init() {
        this.flowOperationsService = new FlowOperationsService(repositoryFactory);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) {
        List<? extends InfoData> result = null;
        if (request instanceof GetFlowsForIslRequest) {
            result = processGetFlowsForLinkRequest((GetFlowsForIslRequest) request);
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
            return flowOperationsService.getFlowIdsForLink(srcSwitch, srcPort, dstSwitch, dstPort).stream()
                    .map(FlowPair::getForward)
                    .map(FlowMapper.INSTANCE::map)
                    .map(FlowResponse::new)
                    .collect(Collectors.toList());
        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("response", "correlationId"));
    }
}
