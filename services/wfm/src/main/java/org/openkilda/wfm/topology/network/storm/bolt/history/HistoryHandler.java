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

package org.openkilda.wfm.topology.network.storm.bolt.history;

import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.network.service.NetworkPortHistoryService;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.history.command.HistoryCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRouter;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.windowing.TupleWindow;

import java.util.Map;

public class HistoryHandler extends BaseWindowedBolt {
    public static final String BOLT_ID = ComponentId.HISTORY_HANDLER.toString();

    private transient NetworkPortHistoryService networkPortHistoryService;
    private transient PortInfoDataAggregator portInfoDataAggregator = null;
    private transient NestedBolt nestedBolt;
    private final PersistenceManager persistenceManager;

    public HistoryHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        nestedBolt = new NestedBolt();
        nestedBolt.prepare(stormConf, context, collector);
        networkPortHistoryService = new NetworkPortHistoryService(persistenceManager);
    }

    @Override
    public void execute(TupleWindow inputWindow) {
        try {
            for (Tuple tuple: inputWindow.get()) {
                nestedBolt.execute(tuple);
            }
        } finally {
            handlePortInfo();
            cleanContext();
        }
    }



    public void processSwitchPortEvent(PortInfoData portInfoData) {
        getPortInfoDataAggregator().add(portInfoData);
    }

    private PortInfoDataAggregator getPortInfoDataAggregator() {
        if (portInfoDataAggregator == null) {
            portInfoDataAggregator = new PortInfoDataAggregator();
        }
        return portInfoDataAggregator;
    }

    private void handlePortInfo() {
        if (portInfoDataAggregator != null) {
            portInfoDataAggregator.getPortInfos().forEach(networkPortHistoryService::savePortInfoData);
        }
    }

    private void cleanContext() {
        portInfoDataAggregator = null;
    }

    class NestedBolt extends AbstractBolt {

        @Override
        protected void handleInput(Tuple input) throws Exception {
            HistoryCommand command = pullValue(input, SpeakerRouter.FIELD_ID_COMMAND, HistoryCommand.class);
            command.apply(HistoryHandler.this);
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {

        }
    }
}
