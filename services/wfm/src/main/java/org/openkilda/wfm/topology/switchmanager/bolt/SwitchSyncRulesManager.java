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

package org.openkilda.wfm.topology.switchmanager.bolt;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.BatchInstallResponse;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt.CoordinatorCommand;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.SwitchSyncRulesCarrier;
import org.openkilda.wfm.topology.switchmanager.command.RemoveKeyRouterBolt;
import org.openkilda.wfm.topology.switchmanager.service.SyncRulesService;
import org.openkilda.wfm.topology.switchmanager.service.impl.SyncRulesServiceImpl;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class SwitchSyncRulesManager extends HubBolt implements SwitchSyncRulesCarrier {
    public static final String ID = "switch.sync.rules";
    public static final String INCOME_STREAM = "sync.rules.command";
    private static final int TIMEOUT_MS = 10000;
    private static final boolean AUTO_ACK = true;

    private final PersistenceManager persistenceManager;
    private transient SyncRulesService service;

    public SwitchSyncRulesManager(String requestSenderComponent, PersistenceManager persistenceManager) {
        super(HubBolt.Config.builder()
                .requestSenderComponent(requestSenderComponent)
                .timeoutMs(TIMEOUT_MS)
                .autoAck(AUTO_ACK)
                .build());

        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        service = new SyncRulesServiceImpl(this, persistenceManager);
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        Message message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof CommandMessage) {
            CommandData data = ((CommandMessage) message).getData();
            if (data instanceof SwitchRulesSyncRequest) {
                service.handleSyncRulesRequest(key, (SwitchRulesSyncRequest) data);
            }

        } else if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof SwitchFlowEntries) {
                service.handleFlowEntriesResponse(key, (SwitchFlowEntries) data);
            } else if (data instanceof BatchInstallResponse) {
                service.handleInstallRulesResponse(key);
            }

        } else if (message instanceof ErrorMessage) {
            service.handleTaskError(key, (ErrorMessage) message);
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onTimeout(String key, Tuple input) {
        service.handleTaskTimeout(key);
    }

    @Override
    public void sendCommand(String key, CommandMessage command) {
        getOutput().emit(StreamType.TO_FLOODLIGHT.toString(), new Values(key, command));
    }

    @Override
    public void response(String key, Message message) {
        getOutput().emit(StreamType.TO_NORTHBOUND.toString(), new Values(key, message));
    }

    @Override
    public void endProcessing(String key) {
        cancelCallback(key);
        removeKeyFromRouterBolt(key);
    }

    private void cancelCallback(String key) {
        getOutput().emit(CoordinatorBolt.INCOME_STREAM, new Values(key, CoordinatorCommand.CANCEL_CALLBACK, 0, null));
    }

    private void removeKeyFromRouterBolt(String key) {
        CommandMessage commandMessage =
                new CommandMessage(new RemoveKeyRouterBolt(key), System.currentTimeMillis(), key);
        getOutput().emit(RouterBolt.INCOME_STREAM, new Values(key, commandMessage));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        Fields fields = new Fields(MessageTranslator.KEY_FIELD, MessageTranslator.FIELD_ID_PAYLOAD);
        declarer.declareStream(StreamType.TO_NORTHBOUND.toString(), fields);
        declarer.declareStream(StreamType.TO_FLOODLIGHT.toString(), fields);
        declarer.declareStream(RouterBolt.INCOME_STREAM, fields);
    }
}
