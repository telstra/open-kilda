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

import org.openkilda.model.Isl;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.service.DiscoveryPortService;
import org.openkilda.wfm.topology.discovery.service.IPortCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.command.PortCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslPhysicalDownCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslRemoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslSetupCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListPollCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PortHandler extends AbstractBolt implements IPortCarrier {
    public static final String BOLT_ID = ComponentId.PORT_HANDLER.toString();

    public static final String FIELD_ID_DATAPATH = SwitchHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_COMMAND = "command";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
                                                          FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_POLL_ID = "poll";
    public static final Fields STREAM_POLL_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
                                                               FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    private transient DiscoveryPortService service;

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (SwitchHandler.BOLT_ID.equals(source)) {
            handleSwitchCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleSwitchCommand(Tuple input) throws PipelineException {
        PortCommand command = pullValue(input, SwitchHandler.FIELD_ID_COMMAND, PortCommand.class);
        command.apply(service, this);
    }

    @Override
    protected void init() {
        service = new DiscoveryPortService(this);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
        streamManager.declareStream(STREAM_POLL_ID, STREAM_POLL_FIELDS);
    }

    @Override
    public void setupUniIslHandler(Endpoint endpoint, Isl history) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslSetupCommand(endpoint, history)));
    }

    @Override
    public void enableDiscoveryPoll(Endpoint endpoint) {
        emit(STREAM_POLL_ID, getCurrentTuple(), makePollTuple(new WatchListPollCommand(endpoint, true)));
    }

    @Override
    public void disableDiscoveryPoll(Endpoint endpoint) {
        emit(STREAM_POLL_ID, getCurrentTuple(), makePollTuple(new WatchListPollCommand(endpoint, false)));
    }

    @Override
    public void notifyPortPhysicalDown(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslPhysicalDownCommand(endpoint)));
    }

    @Override
    public void removeUniIslHandler(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new UniIslRemoveCommand(endpoint)));
    }

    private Values makeDefaultTuple(UniIslCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, safePullContext());
    }

    private Values makePollTuple(WatchListCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, safePullContext());
    }
}
