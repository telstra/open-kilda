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

package org.openkilda.wfm.topology.discovery.storm.bolt.watchlist;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.service.DiscoveryWatchListService;
import org.openkilda.wfm.topology.discovery.service.IWatchListCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.PortHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherAddCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.command.WatcherRemoveCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.command.WatchListCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class WatchListHandler extends AbstractBolt implements IWatchListCarrier {
    public static final String BOLT_ID = ComponentId.WATCH_LIST.toString();

    public static final String FIELD_ID_DATAPATH = PortHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = PortHandler.FIELD_ID_PORT_NUMBER;
    public static final String FIELD_ID_COMMAND = PortHandler.FIELD_ID_COMMAND;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER, FIELD_ID_COMMAND,
            FIELD_ID_CONTEXT);

    private final DiscoveryOptions options;

    private transient DiscoveryWatchListService service;

    public WatchListHandler(DiscoveryOptions options) {
        this.options = options;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (CoordinatorSpout.ID.equals(source)) {
            handleTimer(input);
        } else if (PortHandler.BOLT_ID.equals(source)) {
            handlePortCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleTimer(Tuple input) {
        Long timeMs = input.getLongByField(CoordinatorSpout.FIELD_ID_TIME_MS);
        service.tick(this, timeMs);
    }

    private void handlePortCommand(Tuple input) throws PipelineException {
        WatchListCommand command = pullValue(input, PortHandler.FIELD_ID_COMMAND, WatchListCommand.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        service = new DiscoveryWatchListService(options.getDiscoveryIntervalMs());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }

    @Override
    public void watchRemoved(Endpoint endpoint) {
        emit(getCurrentTuple(), makeDefaultTuple(new WatcherRemoveCommand(endpoint)));
    }

    @Override
    public void discoveryRequest(Endpoint endpoint, long currentTime) {
        emit(getCurrentTuple(), makeDefaultTuple(new WatcherAddCommand(endpoint, currentTime)));
    }

    private Values makeDefaultTuple(WatcherCommand command) {
        Endpoint endpoint = command.getEndpoint();
        CommandContext forkedContext = getCommandContext()
                .fork(endpoint.getDatapath().toString())
                .fork(String.format("p%d", endpoint.getPortNumber()));
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, forkedContext);
    }

    // WatchListCommand

    public void processAddWatch(Endpoint endpoint) {
        service.addWatch(this, endpoint);
    }

    public void processRemoveWatch(Endpoint endpoint) {
        service.removeWatch(this, endpoint);
    }
}
