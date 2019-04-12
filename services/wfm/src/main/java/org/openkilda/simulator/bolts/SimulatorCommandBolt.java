/* Copyright 2018 Telstra Open Source
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

package org.openkilda.simulator.bolts;

import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.classes.SimulatorCommands;
import org.openkilda.simulator.messages.SwitchMessage;
import org.openkilda.simulator.messages.simulator.SimulatorMessage;
import org.openkilda.simulator.messages.simulator.command.AddLinkCommandMessage;
import org.openkilda.simulator.messages.simulator.command.AddSwitchCommand;
import org.openkilda.simulator.messages.simulator.command.PortModMessage;
import org.openkilda.simulator.messages.simulator.command.SwitchModMessage;
import org.openkilda.simulator.messages.simulator.command.TopologyMessage;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatorCommandBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(SimulatorCommandBolt.class);
    private OutputCollector collector;

    protected Map<String, Object> doCommand(Tuple tuple) throws Exception {
        SimulatorMessage message = (SimulatorMessage) tuple.getValueByField(AbstractTopology.MESSAGE_FIELD);
        List<Values> values = new ArrayList<>();
        String stream = "";
        if (message instanceof SwitchModMessage) {
            Values value = new Values(((SwitchModMessage) message).getDpid(), message, SimulatorCommands.DO_SWITCH_MOD);
            values.add(value);
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
        } else if (message instanceof PortModMessage) {
            Values value = new Values(((PortModMessage) message).getDpid(), message, SimulatorCommands.DO_PORT_MOD);
            values.add(value);
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
        } else if (message instanceof TopologyMessage) {
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
            values = createTopology((TopologyMessage) message);
        } else if (message instanceof AddLinkCommandMessage) {
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
            values.add(new Values(((AddLinkCommandMessage) message).getDpid(), message, SimulatorCommands.DO_ADD_LINK));
        } else if (message instanceof AddSwitchCommand) {
            stream = SimulatorTopology.SIMULATOR_COMMAND_STREAM;
            values.add(new Values(((AddSwitchCommand) message).getDpid(), message, SimulatorCommands.DO_ADD_SWITCH));
        } else {
            logger.error("Unknown simulator command received: {}\n{}",
                    message.getClass().getName(), tuple.toString());
        }

        Map<String, Object> map = new HashMap<>();
        if (values.size() > 0) {
            map.put("stream", stream);
            map.put("values", values);
        }

        return map;
    }

    //private List<Integer> emit(String stream, Tuple tuple, List<Values> values) throws Exception {
    private List<Integer> emit(Map<String, Object> map) {
        List<Integer> workers = null;
        List<Values> values = (List<Values>) map.get("values");
        String stream = (String) map.get("stream");
        if (values.size() > 0) {
            for (Values value : values) {
                workers = collector.emit(stream, value);
            }
        }
        return workers;
    }

    private List<Values> createTopology(TopologyMessage topology) {
        List<SwitchMessage> switches = topology.getSwitches();
        List<Values> values = new ArrayList<>();
        for (SwitchMessage sw : switches) {
            // Need to pre-pend 00:00 as DPID as returned by OpenflowJ has 8 octets
            values.add(new Values(String.format("00:00:%s", sw.getDpid()).toLowerCase(), sw,
                    SimulatorCommands.DO_ADD_SWITCH));
            logger.info("Add Switch: {}", sw.getDpid());
        }
        return values;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            String tupleSource = tuple.getSourceComponent();
            switch (tupleSource) {
                case SimulatorTopology.SIMULATOR_SPOUT:
                    emit(doCommand(tuple));
                    break;
                default:
                    logger.error("received command from UNKNOWN source: {}", tupleSource);
            }
        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        //        outputFieldsDeclarer.declareStream(SimulatorTopology.SIMULATOR_COMMAND_STREAM,
        //                new Fields("dpid", "data"));
        outputFieldsDeclarer.declareStream(SimulatorTopology.SIMULATOR_COMMAND_STREAM,
                new Fields("dpid", "data", "command"));
    }
}
