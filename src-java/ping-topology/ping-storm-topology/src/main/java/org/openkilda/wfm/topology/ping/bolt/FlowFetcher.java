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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextRequired;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final FlowResourcesConfig flowResourcesConfig;
    private transient FlowResourcesManager flowResourcesManager;
    private transient FlowRepository flowRepository;
    private Set<FlowWithTransitEncapsulation> flowsSet;
    private long periodicPingCacheExpiryInterval;
    private long lastPeriodicPingCacheRefresh;

    public FlowFetcher(PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig,
                       long periodicPingCacheExpiryInterval) {
        super(persistenceManager);
        this.flowResourcesConfig = flowResourcesConfig;
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
            flowRepository.findById(periodicPingCommand.getFlowId())
                    .flatMap(flow -> {
                        flowRepository.detach(flow);
                        return getFlowWithTransitEncapsulation(flow);
                    })
                    .ifPresent(flowsSet::add);
        } else {
            flowsSet.removeIf(flowWithTransitEncapsulation ->
                    flowWithTransitEncapsulation.getFlow().getFlowId().equals(periodicPingCommand.getFlowId()));
        }
    }

    private void refreshHeap(Tuple input, boolean emitCacheExpiry) throws PipelineException {
        log.debug("Handle periodic ping request");
        Set<FlowWithTransitEncapsulation> flowsWithTransitEncapsulation =
                flowRepository.findWithPeriodicPingsEnabled().stream()
                        .peek(flowRepository::detach)
                        .map(this::getFlowWithTransitEncapsulation)
                        .flatMap(o -> o.isPresent() ? Stream.of(o.get()) : Stream.empty())
                        .collect(Collectors.toSet());
        if (emitCacheExpiry) {
            final CommandContext commandContext = pullContext(input);
            emitCacheExpire(input, commandContext, flowsWithTransitEncapsulation);
        }
        flowsSet = flowsWithTransitEncapsulation;
        lastPeriodicPingCacheRefresh = System.currentTimeMillis();
    }

    private void handlePeriodicRequest(Tuple input) throws PipelineException {
        log.debug("Handle periodic ping request");

        if (lastPeriodicPingCacheRefresh + periodicPingCacheExpiryInterval < System.currentTimeMillis()) {
            refreshHeap(input, true);
        }
        final CommandContext commandContext = pullContext(input);
        for (FlowWithTransitEncapsulation flow : flowsSet) {
            PingContext pingContext = PingContext.builder()
                    .kind(Kinds.PERIODIC)
                    .flow(flow.getFlow())
                    .transitEncapsulation(flow.getTransitEncapsulation())
                    .build();
            emit(input, pingContext, commandContext);
        }

    }

    private void handleOnDemandRequest(Tuple input) throws PipelineException {
        log.debug("Handle on demand ping request");
        FlowPingRequest request = pullOnDemandRequest(input);

        Optional<Flow> optionalFlow = flowRepository.findById(request.getFlowId());

        if (optionalFlow.isPresent()) {
            Flow flow = optionalFlow.get();
            flowRepository.detach(flow);

            if (!flow.isOneSwitchFlow()) {
                Optional<FlowTransitEncapsulation> transitEncapsulation = getTransitEncapsulation(flow);
                if (transitEncapsulation.isPresent()) {
                    PingContext pingContext = PingContext.builder()
                            .kind(Kinds.ON_DEMAND)
                            .flow(flow)
                            .transitEncapsulation(transitEncapsulation.get())
                            .timeout(request.getTimeout())
                            .build();
                    emit(input, pingContext, pullContext(input));
                } else {
                    emitOnDemandResponse(input, request, String.format(
                            "Encapsulation resource not found for flow %s", request.getFlowId()));
                }
            } else {
                emitOnDemandResponse(input, request, String.format(
                        "Flow %s should not be one switch flow", request.getFlowId()));
            }
        } else {
            emitOnDemandResponse(input, request, String.format(
                    "Flow %s does not exist", request.getFlowId()));
        }
    }

    private Optional<FlowWithTransitEncapsulation> getFlowWithTransitEncapsulation(Flow flow) {
        if (!flow.isOneSwitchFlow()) {
            return getTransitEncapsulation(flow)
                    .map(transitEncapsulation -> new FlowWithTransitEncapsulation(flow, transitEncapsulation));
        }
        return Optional.empty();
    }

    private Optional<FlowTransitEncapsulation> getTransitEncapsulation(Flow flow) {
        return flowResourcesManager.getEncapsulationResources(flow.getForwardPathId(),
                flow.getReversePathId(), flow.getEncapsulationType())
                .map(resources ->
                        new FlowTransitEncapsulation(resources.getTransitEncapsulationId(),
                                resources.getEncapsulationType()));
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

    private void emitCacheExpire(Tuple input, CommandContext commandContext, Set<FlowWithTransitEncapsulation> flows) {
        OutputCollector collector = getOutput();
        flowsSet.removeAll(flows);
        for (FlowWithTransitEncapsulation flow : flowsSet) {
            Values output = new Values(flow.getFlow(), commandContext);
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
    @PersistenceContextRequired(requiresNew = true)
    public void init() {
        super.init();

        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        try {
            refreshHeap(null, false);
        } catch (PipelineException e) {
            log.error("Failed to init periodic ping cache");
        }
    }

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode(exclude = {"transitEncapsulation"})
    private static class FlowWithTransitEncapsulation {
        Flow flow;
        FlowTransitEncapsulation transitEncapsulation;
    }
}
