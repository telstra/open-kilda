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

package org.openkilda.wfm.topology.network.storm.bolt.sw;

import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.network.service.ISwitchCarrier;
import org.openkilda.wfm.topology.network.service.NetworkSwitchService;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortLinkStatusCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortOnlineModeCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortRemoveCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortSetupCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortLinkStatusCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortOnlineModeCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortRemoveCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortSetupCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchCommand;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.SwitchManagerWorker;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.command.SwitchManagerSynchronizeSwitchCommand;
import org.openkilda.wfm.topology.network.storm.spout.NetworkHistory;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SwitchHandler extends AbstractBolt implements ISwitchCarrier {
    public static final String BOLT_ID = ComponentId.SWITCH_HANDLER.toString();

    public static final String FIELD_ID_DATAPATH = SpeakerRouter.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_COMMAND = "command";

    public static final String FIELD_ID_KEY = MessageKafkaTranslator.FIELD_ID_KEY;

    public static final String STREAM_SWMANAGER_ID = "swmanager";
    public static final Fields STREAM_SWMANAGER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_PORT_ID = "port";
    public static final Fields STREAM_PORT_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_BFD_PORT_ID = "bfd-port";
    public static final Fields STREAM_BFD_PORT_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_COMMAND,
            FIELD_ID_CONTEXT);

    private final NetworkOptions options;
    private final PersistenceManager persistenceManager;

    private transient NetworkSwitchService service;

    public SwitchHandler(NetworkOptions options, PersistenceManager persistenceManager) {
        this.options = options;
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String source = input.getSourceComponent();

        if (SpeakerRouter.BOLT_ID.equals(source)) {
            handleSpeakerInput(input);
        } else if (SwitchManagerWorker.BOLT_ID.equals(source)) {
            handleSwitchManagerWorkerInput(input);
        } else if (NetworkHistory.SPOUT_ID.equals(source)) {
            handleHistoryInput(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleHistoryInput(Tuple input) throws PipelineException {
        handleCommand(input, NetworkHistory.FIELD_ID_PAYLOAD);
    }

    private void handleSpeakerInput(Tuple input) throws PipelineException {
        handleCommand(input, SpeakerRouter.FIELD_ID_COMMAND);
    }

    private void handleSwitchManagerWorkerInput(Tuple input) throws PipelineException {
        handleCommand(input, SwitchManagerWorker.FIELD_ID_PAYLOAD);
    }

    private void handleCommand(Tuple input, String field) throws PipelineException {
        SwitchCommand command = pullValue(input, field, SwitchCommand.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        service = new NetworkSwitchService(this, persistenceManager, options);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_PORT_ID, STREAM_PORT_FIELDS);
        streamManager.declareStream(STREAM_BFD_PORT_ID, STREAM_BFD_PORT_FIELDS);
        streamManager.declareStream(STREAM_SWMANAGER_ID, STREAM_SWMANAGER_FIELDS);
    }

    @Override
    public void setupPortHandler(Endpoint endpoint, Isl history) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortSetupCommand(endpoint, history)));
    }

    @Override
    public void removePortHandler(Endpoint endpoint) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortRemoveCommand(endpoint)));
    }

    @Override
    public void setOnlineMode(Endpoint endpoint, boolean mode) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortOnlineModeCommand(endpoint, mode)));
    }

    @Override
    public void setPortLinkMode(Endpoint endpoint, LinkStatus linkStatus) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortLinkStatusCommand(endpoint, linkStatus)));
    }

    @Override
    public void setupBfdPortHandler(Endpoint endpoint, int physicalPortNumber) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(
                new BfdPortSetupCommand(endpoint, physicalPortNumber)));
    }

    @Override
    public void removeBfdPortHandler(Endpoint logicalEndpoint) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortRemoveCommand(logicalEndpoint)));
    }

    @Override
    public void setBfdPortLinkMode(Endpoint logicalEndpoint, LinkStatus linkStatus) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(
                new BfdPortLinkStatusCommand(logicalEndpoint, linkStatus)));
    }

    @Override
    public void setBfdPortOnlineMode(Endpoint endpoint, boolean mode) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortOnlineModeCommand(endpoint, mode)));
    }

    @Override
    public void sendSwitchSynchronizeRequest(String key, SwitchId switchId) {
        emit(STREAM_SWMANAGER_ID, getCurrentTuple(), makeSwitchManagerWorkerTuple(key, switchId));
    }

    private Values makePortTuple(PortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext context = forkContext(endpoint.toString());
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, context);
    }

    private Values makeBfdPortTuple(BfdPortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext context = forkContext(endpoint.toString());
        return new Values(endpoint.getDatapath(), command, context);
    }

    private Values makeSwitchManagerWorkerTuple(String key, SwitchId switchId) {
        return new Values(
                key, new SwitchManagerSynchronizeSwitchCommand(key, switchId), getCommandContext().fork("sync"));
    }

    // SwitchCommand processing

    public void processSwitchEvent(SwitchInfoData payload) {
        service.switchEvent(payload);
    }

    public void processSwitchManagerResponse(SwitchSyncResponse payload) {
        service.switchManagerResponse(payload, getKey());
    }

    public void processSwitchManagerErrorResponse(SwitchSyncErrorData payload) {
        service.switchManagerErrorResponse(payload, getKey());
    }

    public void processSwitchManagerTimeout(SwitchId switchId) {
        service.switchManagerTimeout(switchId, getKey());
    }

    private String getKey() {
        return getCurrentTuple().getStringByField(FIELD_ID_KEY);
    }

    public void processSwitchBecomeUnmanaged(SwitchId datapath) {
        service.switchBecomeUnmanaged(datapath);
    }

    public void processSwitchBecomeManaged(SpeakerSwitchView switchView) {
        service.switchBecomeManaged(switchView);
    }

    public void processSwitchAddWithHistory(HistoryFacts history) {
        service.switchAddWithHistory(history);
    }

    public void processSwitchPortEvent(PortInfoData payload) {
        service.switchPortEvent(payload);
    }

    public void processRemoveSwitch(SwitchId datapath) {
        service.remove(datapath);
    }
}
