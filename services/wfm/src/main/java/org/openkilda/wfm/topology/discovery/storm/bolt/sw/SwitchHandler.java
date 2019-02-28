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

package org.openkilda.wfm.topology.discovery.storm.bolt.sw;

import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.model.Isl;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;
import org.openkilda.wfm.topology.discovery.service.DiscoverySwitchService;
import org.openkilda.wfm.topology.discovery.service.ISwitchCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdPortCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdPortLinkStatusCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdPortOnlineModeCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdPortRemoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdPortSetupCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortLinkStatusCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortOnlineModeCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortRemoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortSetupCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.command.SwitchCommand;
import org.openkilda.wfm.topology.discovery.storm.spout.NetworkHistory;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SwitchHandler extends AbstractBolt implements ISwitchCarrier {
    public static final String BOLT_ID = ComponentId.SWITCH_HANDLER.toString();

    public static final String FIELD_ID_DATAPATH = SpeakerRouter.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_COMMAND = "command";

    public static final String STREAM_PORT_ID = "port";
    public static final Fields STREAM_PORT_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_BFD_PORT_ID = "bfd-port";
    public static final Fields STREAM_BFD_PORT_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_COMMAND,
            FIELD_ID_CONTEXT);

    private final DiscoveryOptions options;
    private final PersistenceManager persistenceManager;

    private transient DiscoverySwitchService service;

    public SwitchHandler(DiscoveryOptions options, PersistenceManager persistenceManager) {
        this.options = options;
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String source = input.getSourceComponent();

        if (SpeakerRouter.BOLT_ID.equals(source)) {
            handleSpeakerInput(input);
        } else if (NetworkHistory.SPOUT_ID.equals(source)) {
            handleHistoryInput(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleHistoryInput(Tuple input) throws PipelineException {
        SwitchCommand command = pullValue(input, NetworkHistory.FIELD_ID_PAYLOAD, SwitchCommand.class);
        command.apply(this);
    }

    private void handleSpeakerInput(Tuple input) throws PipelineException {
        SwitchCommand command = pullValue(input, SpeakerRouter.FIELD_ID_COMMAND, SwitchCommand.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        service = new DiscoverySwitchService(this, persistenceManager, options.getBfdLogicalPortOffset());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_PORT_ID, STREAM_PORT_FIELDS);
        streamManager.declareStream(STREAM_BFD_PORT_ID, STREAM_BFD_PORT_FIELDS);
    }

    @Override
    public void setupPortHandler(PortFacts portFacts, Isl history) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortSetupCommand(portFacts, history)));
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
    public void setPortLinkMode(PortFacts port) {
        emit(STREAM_PORT_ID, getCurrentTuple(), makePortTuple(new PortLinkStatusCommand(port)));
    }

    @Override
    public void setupBfdPortHandler(BfdPortFacts portFacts) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortSetupCommand(portFacts)));
    }

    @Override
    public void removeBfdPortHandler(Endpoint logicalEndpoint) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortRemoveCommand(logicalEndpoint)));
    }

    @Override
    public void setBfdPortLinkMode(PortFacts portFacts) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortLinkStatusCommand(portFacts)));
    }

    @Override
    public void setBfdPortOnlineMode(Endpoint endpoint, boolean mode) {
        emit(STREAM_BFD_PORT_ID, getCurrentTuple(), makeBfdPortTuple(new BfdPortOnlineModeCommand(endpoint, mode)));
    }

    private Values makePortTuple(PortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext context = makeContextFork(endpoint.getPortNumber());
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, context);
    }

    private Values makeBfdPortTuple(BfdPortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext context = makeContextFork(endpoint.getPortNumber());
        return new Values(endpoint.getDatapath(), command, context);
    }

    private CommandContext makeContextFork(int portNumber) {
        try {
            return pullContext().fork(String.format("p%d", portNumber));
        } catch (PipelineException e) {
            final Tuple input = getCurrentTuple();
            throw new IllegalStateException(String.format("Missing %s into %s in tuple from %s(%s)",
                                                          CommandContext.class.getName(), getClass().getName(),
                                                          input.getSourceComponent(), input.getSourceStreamId()));
        }
    }

    // SwitchCommand processing

    public void processSwitchEvent(SwitchInfoData payload) {
        service.switchEvent(payload);
    }

    public void processSwitchEvent(UnmanagedSwitchNotification payload) {
        service.switchEvent(payload);
    }

    public void processSwitchAddWithHistory(HistoryFacts history) {
        service.switchAddWithHistory(history);
    }

    public void processSwitchPortEvent(PortInfoData payload) {
        service.switchPortEvent(payload);
    }
}
