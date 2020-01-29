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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.flow.FlowPingRequest;
import org.openkilda.messaging.command.flow.PeriodicPingCommand;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.model.FlowPair;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FlowFetcher extends Abstract {
    public static final String BOLT_ID = ComponentId.FLOW_FETCHER.toString();

    public static final String FIELD_ID_FLOW_ID = Utils.FLOW_ID;
    public static final String FIELD_FLOW_REF = "flow_ref";
    public static final String FIELD_ID_ON_DEMAND_RESPONSE = NorthboundEncoder.FIELD_ID_PAYLOAD;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_FLOW_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);

    public static final Fields STREAM_EXPIRE_CACHE_FIELDS = new Fields(FIELD_FLOW_REF, FIELD_ID_CONTEXT);
    public static final String STREAM_EXPIRE_CACHE_ID = "expire_cache";

    public static final Fields STREAM_ON_DEMAND_RESPONSE_FIELDS = new Fields(
            FIELD_ID_ON_DEMAND_RESPONSE, FIELD_ID_CONTEXT);
    public static final String STREAM_ON_DEMAND_RESPONSE_ID = "on_demand_response";

    private final PersistenceManager persistenceManager;
    private transient FlowPairRepository flowPairRepository;
    private Set<BidirectionalFlowDto> flowsSet;
    private long periodicPingCacheExpiryInterval;
    private long lastPeriodicPingCacheRefresh;

    public FlowFetcher(PersistenceManager persistenceManager, long periodicPingCacheExpiryInterval) {
        this.persistenceManager = persistenceManager;
        this.periodicPingCacheExpiryInterval = TimeUnit.SECONDS.toMillis(periodicPingCacheExpiryInterval);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String component = input.getSourceComponent();

        if (TickDeduplicator.BOLT_ID.equals(component)) {
            handlePeriodicRequest(input);
        } else if (InputRouter.BOLT_ID.equals(component)) {
            String sourceStream = input.getSourceStreamId();
            if (InputRouter.STREAM_ON_DEMAND_REQUEST_ID.equals(sourceStream)) {
                handleOnDemandRequest(input);
            } else if (InputRouter.STREAM_PERIODIC_PING_UPDATE_REQUEST_ID.equals(sourceStream)) {
                updatePeriodicPingHeap(input);
            }
        } else {
            unhandledInput(input);
        }
    }

    private void updatePeriodicPingHeap(Tuple input) throws PipelineException {
        PeriodicPingCommand periodicPingCommand = pullPeriodicPingRequest(input);
        if (periodicPingCommand.isEnable()) {
            Optional<FlowPair> flowPair = flowPairRepository.findById(periodicPingCommand.getFlowId());
            if (flowPair.isPresent()) {
                BidirectionalFlowDto flowDto = new BidirectionalFlowDto(FlowMapper.INSTANCE.map(flowPair.get()));
                flowsSet.add(flowDto);
            }
        } else {
            flowsSet.removeIf(flow -> flow.getFlowId().equals(periodicPingCommand.getFlowId()));
        }
    }

    private void refreshHeap(Tuple input, boolean emitCacheExpiry) throws PipelineException {
        log.debug("Handle periodic ping request");
        final Set<BidirectionalFlowDto> flows = new HashSet<>();
        Collection<FlowPair> flowPairs = flowPairRepository.findWithPeriodicPingsEnabled();
        for (FlowPair fp : flowPairs) {
            try {
                flows.add(new BidirectionalFlowDto(FlowMapper.INSTANCE.map(fp)));
            } catch (Exception e) {
                String forwardPathId = null;
                if (fp != null && fp.forward != null && fp.forward.getFlowPath() != null
                        && fp.forward.getFlowPath().getPathId() != null) {
                    forwardPathId = fp.forward.getFlowPath().getPathId().getId();
                }
                String reversePathId = null;
                if (fp != null && fp.reverse != null && fp.reverse.getFlowPath() != null
                        && fp.reverse.getFlowPath().getPathId() != null) {
                    reversePathId = fp.reverse.getFlowPath().getPathId().getId();
                }

                String message = String.format("Failed to build flow from paths. Forward path id '%s', "
                        + "reverse path id '%s'. Skipping.", forwardPathId, reversePathId);
                log.info(message, e);
            }
        }
        if (emitCacheExpiry) {
            final CommandContext commandContext = pullContext(input);
            emitCacheExpire(input, commandContext, flows);
        }
        flowsSet = flows;
        lastPeriodicPingCacheRefresh = System.currentTimeMillis();
    }

    private void handlePeriodicRequest(Tuple input) throws PipelineException {
        log.debug("Handle periodic ping request");

        if (lastPeriodicPingCacheRefresh + periodicPingCacheExpiryInterval < System.currentTimeMillis()) {
            refreshHeap(input, true);
        }
        final CommandContext commandContext = pullContext(input);
        for (BidirectionalFlowDto flow : flowsSet) {
            PingContext pingContext = new PingContext(Kinds.PERIODIC, flow);
            emit(input, pingContext, commandContext);
        }

    }

    private void handleOnDemandRequest(Tuple input) throws PipelineException {
        log.debug("Handle on demand ping request");
        FlowPingRequest request = pullOnDemandRequest(input);
        BidirectionalFlowDto flow;

        Optional<FlowPair> flowPair = flowPairRepository.findById(request.getFlowId());
        if (!flowPair.isPresent()) {
            emitOnDemandResponse(input, request, String.format(
                    "Flow %s does not exist", request.getFlowId()));
            return;
        }

        flow = new BidirectionalFlowDto(FlowMapper.INSTANCE.map(flowPair.get()));

        PingContext pingContext = new PingContext(Kinds.ON_DEMAND, flow).toBuilder()
                .timeout(request.getTimeout())
                .build();
        emit(input, pingContext, pullContext(input));
    }

    private void emit(Tuple input, PingContext pingContext, CommandContext commandContext) {
        Values output = new Values(pingContext.getFlowId(), pingContext, commandContext);
        getOutput().emit(input, output);
    }

    private void emitOnDemandResponse(Tuple input, FlowPingRequest request, String errorMessage)
            throws PipelineException {
        FlowPingResponse response = new FlowPingResponse(request.getFlowId(), errorMessage);
        Values output = new Values(response, pullContext(input));
        getOutput().emit(STREAM_ON_DEMAND_RESPONSE_ID, input, output);
    }

    private void emitCacheExpire(Tuple input, CommandContext commandContext, Set<BidirectionalFlowDto> flows) {
        OutputCollector collector = getOutput();
        flowsSet.removeAll(flows);
        for (BidirectionalFlowDto flow : flowsSet) {
            Values output = new Values(flow, commandContext);
            collector.emit(STREAM_EXPIRE_CACHE_ID, input, output);

        }
    }

    private FlowPingRequest pullOnDemandRequest(Tuple input) throws PipelineException {
        return pullValue(input, InputRouter.FIELD_ID_PING_REQUEST, FlowPingRequest.class);
    }

    private PeriodicPingCommand pullPeriodicPingRequest(Tuple input) throws PipelineException {
        return pullValue(input, InputRouter.FIELD_ID_PING_REQUEST, PeriodicPingCommand.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
        outputManager.declareStream(STREAM_EXPIRE_CACHE_ID, STREAM_EXPIRE_CACHE_FIELDS);
        outputManager.declareStream(STREAM_ON_DEMAND_RESPONSE_ID, STREAM_ON_DEMAND_RESPONSE_FIELDS);
    }

    @Override
    public void init() {
        flowPairRepository = persistenceManager.getRepositoryFactory().createFlowPairRepository();
        try {
            refreshHeap(null, false);
        } catch (PipelineException e) {
            log.error("Failed to init periodic ping cache");
        }
    }
}
