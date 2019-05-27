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

package org.openkilda.wfm.topology.network.storm.bolt.decisionmaker;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.service.IDecisionMakerCarrier;
import org.openkilda.wfm.topology.network.service.NetworkDecisionMakerService;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.decisionmaker.command.DecisionMakerCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortDiscoveryCommand;
import org.openkilda.wfm.topology.network.storm.bolt.port.command.PortFailCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.WatcherHandler;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class DecisionMakerHandler extends AbstractBolt implements IDecisionMakerCarrier {
    public static final String BOLT_ID = ComponentId.DECISION_MAKER.toString();

    public static final String FIELD_ID_DATAPATH = WatcherHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = WatcherHandler.FIELD_ID_PORT_NUMBER;
    public static final String FIELD_ID_COMMAND = WatcherHandler.FIELD_ID_COMMAND;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER, FIELD_ID_COMMAND,
            FIELD_ID_CONTEXT);

    private final NetworkOptions options;

    private transient NetworkDecisionMakerService service;

    public DecisionMakerHandler(NetworkOptions options) {
        this.options = options;
    }


    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (CoordinatorSpout.ID.equals(source)) {
            handleTimer(input);
        } else if (WatcherHandler.BOLT_ID.equals(source)) {
            handleCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleTimer(Tuple input) {
        service.tick();
    }

    private void handleCommand(Tuple input) throws PipelineException {
        DecisionMakerCommand command = pullValue(input, WatcherHandler.FIELD_ID_COMMAND, DecisionMakerCommand.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        service = new NetworkDecisionMakerService(this,
                                                  options.getDiscoveryTimeout(), options.getDiscoveryPacketTtl());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }

    // IDecisionMakerCarrier

    @Override
    public void linkDiscovered(IslInfoData discoveryEvent) {
        emit(getCurrentTuple(), makeDefaultTuple(new PortDiscoveryCommand(discoveryEvent)));
    }

    @Override
    public void linkDestroyed(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new PortFailCommand(endpoint)));
    }

    // DecisionMakerCommand

    public void processClear(Endpoint endpoint) {
        service.clear(endpoint);
    }

    public void processDiscovered(Endpoint endpoint, long packetId, IslInfoData discoveryEvent) {
        service.discovered(endpoint, packetId, discoveryEvent);
    }

    public void processFailed(Endpoint endpoint, long packetId) {
        service.failed(endpoint, packetId);
    }

    // Private

    private Values makeDefaultTuple(PortCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext context = forkContext("DM", endpoint.toString());
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, context);
    }
}
