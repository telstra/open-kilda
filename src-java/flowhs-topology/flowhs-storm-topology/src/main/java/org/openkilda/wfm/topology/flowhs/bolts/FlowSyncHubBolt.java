/* Copyright 2022 Telstra Open Source
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

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_PING_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_STATS_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.messaging.command.flow.PeriodicPingCommand;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.CarrierContext;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.ComponentId;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResult;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncService;
import org.openkilda.wfm.topology.flowhs.service.path.FlowPathCarrier;
import org.openkilda.wfm.topology.flowhs.service.path.FlowPathService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowSyncHubBolt extends HubBolt implements FlowSyncCarrier, FlowPathCarrier {
    public static final String BOLT_ID = ComponentId.FLOW_SYNC_HUB.name();

    private final FlowSyncConfig config;
    private final FlowResourcesConfig flowResourcesConfig;

    private transient FlowSyncService syncService;
    private transient FlowPathService pathService;

    private transient CarrierContext<String> carrierContext;
    private LifecycleEvent deferredShutdownEvent;

    public FlowSyncHubBolt(
            @NonNull FlowSyncConfig config, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesConfig flowResourcesConfig) {
        super(persistenceManager, config);
        this.config = config;
        this.flowResourcesConfig = flowResourcesConfig;
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        FlowSyncRequest request = pullValue(input, FIELD_ID_PAYLOAD, FlowSyncRequest.class);
        carrierContext.apply(pullKey(input), key -> handleRequest(key, request));
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        SpeakerFlowSegmentResponse response = pullValue(input, FIELD_ID_PAYLOAD, SpeakerFlowSegmentResponse.class);
        carrierContext.apply(pullKey(input), key -> handleWorkerResponse(key, response));
    }

    @Override
    protected void onTimeout(String coordinatorKey, Tuple tuple) throws PipelineException {
        carrierContext.apply(coordinatorKey, key -> syncService.handleTimeout(key));
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        // pathService do not process any external requests (only requests from syncService) so it can to not implement
        // enable/disable feature and do not need to be called here.
        if (syncService.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        syncService.activate();
    }

    private void handleRequest(String serviceKey, FlowSyncRequest request) {
        syncService.handleRequest(serviceKey, request, getCommandContext());
    }

    private void handleWorkerResponse(String workerKey, SpeakerFlowSegmentResponse response) {
        carrierContext.apply(KeyProvider.getParentKey(workerKey), serviceKey -> {
            try {
                // FlowSyncService do not communicate with speaker, so only FlowPathService can consume speaker
                // responses.
                pathService.handleSpeakerResponse(serviceKey, response);
            } catch (UnknownKeyException e) {
                log.warn("Received a speaker response with unknown key {}.", serviceKey);
            }
        });
    }

    private String newPathServiceKey(PathId pathId) {
        return KeyProvider.joinKeys(pathId.toString(), carrierContext.getContext());
    }

    // -- carrier --

    @Override
    public void sendSpeakerRequest(SpeakerRequest request) {
        String requestKey = KeyProvider.joinKeys(request.getCommandId().toString(), carrierContext.getContext());
        emit(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), makeOfSpeakerTuple(requestKey, request));
    }

    @Override
    public void sendPeriodicPingNotification(String flowId, boolean enabled) {
        // TODO(surabujin): ensure usage
        PeriodicPingCommand payload = new PeriodicPingCommand(flowId, enabled);
        emit(Stream.HUB_TO_PING_SENDER.name(), getCurrentTuple(), makePingTuple(payload));
    }

    @Override
    public void sendHistoryUpdate(FlowHistoryHolder payload) {
        emit(Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), getCurrentTuple(), makeHistoryTuple(payload));
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key);
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;
    }

    @Override
    public void sendNorthboundResponse(Message message) {
        emit(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), makeNorthboundTuple(message));
    }

    private Values makeNorthboundTuple(Message message) {
        return new Values(carrierContext.getContext(), message, getCommandContext());
    }

    private Values makeOfSpeakerTuple(String requestKey, SpeakerRequest request) {
        return new Values(requestKey, request, getCommandContext());
    }

    private Values makePingTuple(CommandData payload) {
        CommandContext commandContext = getCommandContext();
        Message message = new CommandMessage(
                payload, commandContext.getCreateTime(), commandContext.getCorrelationId());
        return new Values(carrierContext.getContext(), message, commandContext);
    }

    private Values makeHistoryTuple(FlowHistoryHolder payload) {
        CommandContext commandContext = getCommandContext();
        InfoMessage message = new InfoMessage(payload, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
        return new Values(payload.getTaskId(), message, getCommandContext());
    }

    @Override
    public void launchFlowPathInstallation(
            @NonNull FlowPathRequest request, @NonNull FlowPathOperationConfig config,
            @NonNull CommandContext commandContext) throws DuplicateKeyException {
        try {
            carrierContext.applyUnsafe(newPathServiceKey(
                            request.getReference().getPathId()),
                    pathServiceKey -> pathService.installPath(request, pathServiceKey, config, commandContext));
        } catch (DuplicateKeyException e) {
            throw e;
        } catch (Exception e) {
            carrierContext.throwUnexpectedException(e);
        }
    }

    @Override
    public void cancelFlowPathOperation(PathId pathId) throws UnknownKeyException {
        try {
            carrierContext.applyUnsafe(
                    newPathServiceKey(pathId),
                    pathServiceKey -> pathService.cancelOperation(pathServiceKey));
        } catch (UnknownKeyException e) {
            throw e;
        } catch (Exception e) {
            carrierContext.throwUnexpectedException(e);
        }
    }

    @Override
    public void processFlowPathOperationResults(FlowPathResult result) {
        syncService.handlePathSyncResponse(result.getReference(), result.getResultCode());
    }

    // -- storm API --

    @Override
    protected void init() {
        super.init();

        final FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        final FlowPathOperationConfig pathOperationConfig = new FlowPathOperationConfig(
                config.getSpeakerCommandRetriesLimit());

        pathService = new FlowPathService(this);
        syncService = new FlowSyncService(this, persistenceManager, resourcesManager, pathOperationConfig);

        carrierContext = new CarrierContext<>();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));

        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_PING_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_STATS_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    // -- topology API --

    @Getter
    public static class FlowSyncConfig extends Config {
        private final int speakerCommandRetriesLimit;

        @Builder(builderMethodName = "flowSyncBuilder", builderClassName = "flowSyncBuild")
        public FlowSyncConfig(
                String requestSenderComponent, String workerComponent, String lifeCycleEventComponent, int timeoutMs,
                boolean autoAck, int speakerCommandRetriesLimit) {
            super(requestSenderComponent, workerComponent, lifeCycleEventComponent, timeoutMs, autoAck);
            this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        }
    }
}
