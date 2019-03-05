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

package org.openkilda.wfm.topology.discovery.storm.bolt.port;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.topology.discovery.controller.AntiFlapFsm.Config;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;
import org.openkilda.wfm.topology.discovery.service.DiscoveryAntiFlapService;
import org.openkilda.wfm.topology.discovery.service.DiscoveryPortService;
import org.openkilda.wfm.topology.discovery.service.IAntiFlapCarrier;
import org.openkilda.wfm.topology.discovery.service.IPortCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.decisionmaker.DecisionMakerHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslDiscoveryCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslFailCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslPhysicalDownCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslRemoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslSetupCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListPollAddCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListPollRemoveCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PortHandler extends AbstractBolt implements IPortCarrier, IAntiFlapCarrier {
    public static final String BOLT_ID = ComponentId.PORT_HANDLER.toString();

    public static final String FIELD_ID_DATAPATH = SwitchHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_COMMAND = "command";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_POLL_ID = "poll";
    public static final Fields STREAM_POLL_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    private transient DiscoveryPortService portService;
    private transient DiscoveryAntiFlapService antiFlapService;

    private Config antiFlapConfig;

    public PortHandler(DiscoveryOptions options) {
        this.antiFlapConfig = Config.builder()
                .delayCoolingDown(options.getDelayCoolingDown())
                .delayWarmUp(options.getDelayWarmUp())
                .delayMin(options.getDelayMin())
                .build();
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (DecisionMakerHandler.BOLT_ID.equals(source)) {
            handleDecisionMakerCommand(input);
        } else if (CoordinatorSpout.ID.equals(source)) {
            handleTimer();
        } else if (SwitchHandler.BOLT_ID.equals(source)) {
            handleSwitchCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleDecisionMakerCommand(Tuple input) throws PipelineException {
        handleCommand(input, DecisionMakerHandler.FIELD_ID_COMMAND);
    }

    private void handleSwitchCommand(Tuple input) throws PipelineException {
        handleCommand(input, SwitchHandler.FIELD_ID_COMMAND);
    }

    private void handleCommand(Tuple input, String field) throws PipelineException {
        PortCommand command = pullValue(input, field, PortCommand.class);
        command.apply(this);
    }

    private void handleTimer() {
        antiFlapService.tick();
    }

    @Override
    protected void init() {
        portService = new DiscoveryPortService(this);
        antiFlapService = new DiscoveryAntiFlapService(this, antiFlapConfig);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
        streamManager.declareStream(STREAM_POLL_ID, STREAM_POLL_FIELDS);
    }

    // IPortCarrier

    @Override
    public void setupUniIslHandler(Endpoint endpoint, Isl history) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslSetupCommand(endpoint, history)));
    }

    @Override
    public void enableDiscoveryPoll(Endpoint endpoint) {
        emit(STREAM_POLL_ID, getCurrentTuple(), makePollTuple(new WatchListPollAddCommand(endpoint)));
    }

    @Override
    public void disableDiscoveryPoll(Endpoint endpoint) {
        emit(STREAM_POLL_ID, getCurrentTuple(), makePollTuple(new WatchListPollRemoveCommand(endpoint)));
    }

    @Override
    public void notifyPortDiscovered(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslDiscoveryCommand(endpoint, speakerDiscoveryEvent)));
    }

    @Override
    public void notifyPortDiscoveryFailed(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslFailCommand(endpoint)));
    }

    @Override
    public void notifyPortPhysicalDown(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslPhysicalDownCommand(endpoint)));
    }


    @Override
    public void removeUniIslHandler(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslRemoveCommand(endpoint)));
    }

    // IAntiFlapCarrier

    @Override
    public void filteredLinkStatus(Endpoint endpoint, LinkStatus status) {
        portService.updateLinkStatus(endpoint, status);
    }

    // PortCommand processing

    public void processSetup(PortFacts facts, Isl history) {
        portService.setup(facts, history);
    }

    public void processRemove(Endpoint endpoint) {
        portService.remove(endpoint);
    }

    public void processUpdateOnlineMode(Endpoint endpoint, boolean online) {
        portService.updateOnlineMode(endpoint, online);
    }

    public void processUpdateLinkStatus(Endpoint endpoint, LinkStatus linkStatus) {
        antiFlapService.filterLinkStatus(endpoint, linkStatus);
    }

    public void processDiscovery(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        portService.discovery(endpoint, speakerDiscoveryEvent);
    }

    public void processFail(Endpoint endpoint) {
        portService.fail(endpoint);
    }

    // Private

    private Values makeDefaultTuple(UniIslCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getCommandContext());
    }

    private Values makePollTuple(WatchListCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getCommandContext());
    }
}
