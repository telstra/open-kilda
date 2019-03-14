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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.model.SwitchId;
import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.classes.Commands;
import org.openkilda.wfm.OfeMessageUtils;
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

import java.util.List;
import java.util.Map;

public class CommandBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(CommandBolt.class);
    private OutputCollector collector;

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    protected String getType(String json) throws Exception {
        try {
            Map<String, ?> root = OfeMessageUtils.fromJson(json);
            return ((String) root.get("type")).toLowerCase();
        } catch (Exception e) {
            logger.error("error getting type in: {}", json);
            throw e;
        }
    }

    protected String getJson(Tuple tuple) {
        return tuple.getStringByField(AbstractTopology.MESSAGE_FIELD);
    }

    protected void processCommand(Tuple tuple) throws Exception {
        CommandMessage command = Utils.MAPPER.readValue(getJson(tuple), CommandMessage.class);
        if (command.getDestination() == Destination.CONTROLLER) {
            CommandData data = command.getData();
            Commands switchCommand;
            SwitchId sw;
            if (data instanceof DiscoverIslCommandData) {
                switchCommand = Commands.DO_DISCOVER_ISL_COMMAND;
                sw = ((DiscoverIslCommandData) data).getSwitchId();
            } else if (data instanceof DiscoverPathCommandData) {
                switchCommand = Commands.DO_DISCOVER_PATH_COMMAND;
                sw = ((DiscoverPathCommandData) data).getSrcSwitchId();
            } else if (data instanceof InstallIngressFlow) {
                switchCommand = Commands.DO_INSTALL_INGRESS_FLOW;
                sw = ((InstallIngressFlow) data).getSwitchId();
            } else if (data instanceof InstallEgressFlow) {
                switchCommand = Commands.DO_INSTALL_EGRESS_FLOW;
                sw = ((InstallEgressFlow) data).getSwitchId();
            } else if (data instanceof InstallTransitFlow) {
                switchCommand = Commands.DO_INSTALL_TRANSIT_FLOW;
                sw = ((InstallTransitFlow) data).getSwitchId();
            } else if (data instanceof InstallOneSwitchFlow) {
                switchCommand = Commands.DO_INSTALL_ONESWITCH_FLOW;
                sw = ((InstallOneSwitchFlow) data).getSwitchId();
            } else if (data instanceof RemoveFlow) {
                switchCommand = Commands.DO_DELETE_FLOW;
                sw = ((RemoveFlow) data).getSwitchId();
            } else {
                logger.error("UNKNOWN data type: {}", data.toString());
                throw new Exception(String.format("Unknown command %s", data.getClass().getSimpleName()));
            }
            List<Integer> taskIDs = collector.emit(SimulatorTopology.COMMAND_BOLT_STREAM, tuple,
                    new Values(sw, switchCommand.name(), command.getData()));
            // logger.info("{}:  {} - {}", switchCommand.name(), sw, command.getData().toString());
        }
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            String json = getJson(tuple);
            switch (getType(json)) {
                case "command":
                    processCommand(tuple);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Could not parse tuple: {}", tuple.toString(), e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(SimulatorTopology.COMMAND_BOLT_STREAM,
                new Fields("dpid", SpeakerBolt.TupleFields.COMMAND.name(), SpeakerBolt.TupleFields.DATA.name()));
    }
}
