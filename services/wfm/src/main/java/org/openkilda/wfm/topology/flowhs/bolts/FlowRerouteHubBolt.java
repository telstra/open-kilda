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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_HISTORY_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowRerouteHubBolt extends HubBolt implements FlowRerouteHubCarrier {
    private final PersistenceManager persistenceManager;
    private final PathComputerConfig pathComputerConfig;
    private final FlowResourcesConfig flowResourcesConfig;

    private transient FlowRerouteService service;
    private String currentKey;

    public FlowRerouteHubBolt(String routerBoltId, String workerBoltId, int timeoutMs, boolean autoAck,
                              PersistenceManager persistenceManager, PathComputerConfig pathComputerConfig,
                              FlowResourcesConfig flowResourcesConfig) {
        super(HubBolt.Config.builder()
                .requestSenderComponent(routerBoltId)
                .workerComponent(workerBoltId)
                .timeoutMs(timeoutMs)
                .autoAck(autoAck)
                .build());

        this.persistenceManager = persistenceManager;
        this.pathComputerConfig = pathComputerConfig;
        this.flowResourcesConfig = flowResourcesConfig;
    }

    @Override
    protected void init() {
        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, persistenceManager.getRepositoryFactory());
        PathComputer pathComputer =
                new PathComputerFactory(pathComputerConfig, availableNetworkFactory).getPathComputer();

        FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        service = new FlowRerouteService(this, persistenceManager, pathComputer, resourcesManager);
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        currentKey = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        FlowRerouteRequest request = (FlowRerouteRequest) input.getValueByField(FIELD_ID_PAYLOAD);
        service.handleRequest(currentKey, pullContext(input), request.getFlowId(), request.getPathIds());
    }

    @Override
    protected void onWorkerResponse(Tuple input) {
        String operationKey = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        currentKey = KeyProvider.getParentKey(operationKey);
        FlowResponse flowResponse = (FlowResponse) input.getValueByField(FIELD_ID_PAYLOAD);
        service.handleAsyncResponse(currentKey, flowResponse);
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        service.handleTimeout(key);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_HISTORY_BOLT.name(), MessageTranslator.STREAM_FIELDS);
    }

    @Override
    public void sendSpeakerRequest(FlowRequest command) {
        String commandKey = KeyProvider.joinKeys(command.getCommandId().toString(), currentKey);
        Values values = new Values(commandKey, command);
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    @Override
    public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
        //TODO: fix repeated request processing caused by History bolt
        // emitWithContext(Stream.HUB_TO_HISTORY_BOLT.name(), tuple, new Values(null, historyHolder));
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key, getCurrentTuple());
    }
}
