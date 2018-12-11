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

package org.openkilda.wfm.topology.discovery.storm.bolt.decisionmaker;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.service.DiscoveryDecisionMakerService;
import org.openkilda.wfm.topology.discovery.service.IDecisionMakerCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.decisionmaker.command.DecisionMakerCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslDiscoveryCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.command.UniIslFailCommand;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.WatcherHandler;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class DecisionMakerHandler extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.DECISION_MAKER.toString();

    public static final String FIELD_ID_DATAPATH = WatcherHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = WatcherHandler.FIELD_ID_PORT_NUMBER;
    public static final String FIELD_ID_COMMAND = WatcherHandler.FIELD_ID_COMMAND;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER, FIELD_ID_COMMAND);

    private final DiscoveryOptions options;

    private transient DiscoveryDecisionMakerService service;

    public DecisionMakerHandler(DiscoveryOptions options) {
        this.options = options;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        DecisionMakerCommand command = pullValue(input, WatcherHandler.FIELD_ID_COMMAND, DecisionMakerCommand.class);
        command.apply(service, new OutputAdapter(this, input));
    }

    @Override
    protected void init() {
        service = new DiscoveryDecisionMakerService(options.getDiscoveryTimeout(), options.getDiscoveryPacketTtl());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }

    private static class OutputAdapter extends AbstractOutputAdapter implements IDecisionMakerCarrier {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }

        @Override
        public void linkDiscovered(IslInfoData discoveryEvent) {
            emit(makeDefaultTuple(new UniIslDiscoveryCommand(discoveryEvent)));
        }

        @Override
        public void linkDestroyed(Endpoint endpoint) {
            emit(makeDefaultTuple(new UniIslFailCommand(endpoint)));
        }

        private Values makeDefaultTuple(UniIslCommand command) {
            Endpoint endpoint = command.getEndpoint();
            return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getContext());
        }
    }
}
