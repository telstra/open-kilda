package org.openkilda.simulator.bolts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.state.State;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IStatefulBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.*;
import org.openkilda.simulator.SimulatorTopology;
import org.openkilda.simulator.classes.Commands;
import org.openkilda.simulator.classes.Port;
import org.openkilda.simulator.classes.SimulatorException;
import org.openkilda.simulator.classes.Switch;
import org.openkilda.simulator.messages.LinkMessage;
import org.openkilda.simulator.messages.SwitchMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class SwitchBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger(SwitchBolt.class);
    private OutputCollector collector;
    private Map<String, Switch> switches;
    public enum TupleFields {
        COMMAND,
        DATA;
    }

    protected String makeSwitchMessage(Switch sw, SwitchState state) throws IOException {
        SwitchInfoData data = new SwitchInfoData(
                sw.getDpid().toString(),
                state,
                "192.168.0.1", // TODO: need to create these on the fly
                "sw" + sw.getDpid().toString(),
                "Simulated Switch",
                "SimulatorTopology"
        );
        InfoMessage message = new InfoMessage(
                data,
                Instant.now().toEpochMilli(),
                UUID.randomUUID().toString(),
                null);
        return Utils.MAPPER.writeValueAsString(message);
    }

    protected String makePortMessage(Switch sw, int portNum, PortChangeType type) throws IOException {
        PortInfoData data = new PortInfoData(
                sw.getDpid().toString(),
                portNum,
                type
        );
        InfoMessage message = new InfoMessage(
                data,
                Instant.now().toEpochMilli(),
                UUID.randomUUID().toString(),
                null);
        return Utils.MAPPER.writeValueAsString(message);
    }

    protected List<Values> addSwitch(SwitchMessage switchMessage) throws IOException {
        Switch sw = switches.get(switchMessage.getDpid());
        List<Values> values = new ArrayList<>();

        if (sw == null) {
            logger.info("switch does not exist, adding it");
            sw = new Switch(DatapathId.of(switchMessage.getDpid()), switchMessage.getNumOfPorts());

            List<LinkMessage> links = switchMessage.getLinks();
            for (LinkMessage l : links) {
                Port localPort = sw.getPort(l.getLocalPort());
                localPort.setLatency(l.getLatency());
                localPort.setPeerPortNum(l.getPeerPort());
                localPort.setPeerSwitch(l.getPeerSwitch());
            }

            switches.put(sw.getDpid().toString(), sw);

            values.add(new Values("INFO", makeSwitchMessage(sw, SwitchState.ADDED)));
            values.add(new Values("INFO", makeSwitchMessage(sw, SwitchState.ACTIVATED)));

            for (Port p : sw.getPorts()) {
                values.add(new Values("INFO", makePortMessage(sw, p.getNumber(), PortChangeType.UP)));
            }
        }
        return values;
    }

    protected void discoverIsl(Tuple tuple, DiscoverIslCommandData data) throws Exception {
        logger.info("doing: {}", data.toString());
        Switch sw = getSwitch(data.getSwitchId());
        Port localPort = sw.getPort(data.getPortNo());

        if (localPort.isActiveIsl()) {
            List<PathNode> path = new ArrayList<>();
            PathNode path1 = new PathNode(sw.getDpid().toString(), localPort.getNumber(), 0);
            path1.setSegLatency(localPort.getLatency());
            PathNode path2 = new PathNode(localPort.getPeerSwitch(), localPort.getPeerPortNum(), 1);
            path.add(path1);
            path.add(path2);
            IslInfoData islInfoData = new IslInfoData(
                    localPort.getLatency(),
                    path,
                    100000,
                    IslChangeType.DISCOVERED,
                    100000);
            collector.emit(SimulatorTopology.SWITCH_BOLT_STREAM, tuple,
                    new Values(localPort.getPeerSwitch().toLowerCase(), Commands.DO_DISCOVER_ISL_P2_COMMAND.name(), islInfoData));
        }
    }

    protected void discoverIslPartTwo(Tuple tuple, IslInfoData data) throws Exception {
        Switch sw = getSwitch(data.getPath().get(1).getSwitchId());
        Port port = sw.getPort(data.getPath().get(1).getPortNo());

        if (port.isActiveIsl()) {
            long now = Instant.now().toEpochMilli();
            InfoMessage infoMessage = new InfoMessage(data, now, "system", null);
            collector.emit(SimulatorTopology.KAFKA_BOLT_STREAM, tuple,
                    new Values("INFO", Utils.MAPPER.writeValueAsString(infoMessage)));
        }
    }

    public Switch getSwitch(String name) throws Exception {
        Switch sw = switches.get(name);
        if (sw == null) {
            throw new SimulatorException(String.format("Switch %s not found", name));
        }
        return sw;
    }

    public void doCommand(Tuple tuple) throws Exception {
        String command = tuple.getStringByField(TupleFields.COMMAND.name());
        List<Values> values = new ArrayList<>();

        if (command.equals(Commands.DO_ADD_SWITCH.name())) {
            values = addSwitch((SwitchMessage) tuple.getValueByField(TupleFields.DATA.name()));
            if (values.size() > 0) {
                for (Values value : values) {
                    logger.debug("emitting: {}", value);
                    collector.emit(SimulatorTopology.KAFKA_BOLT_STREAM, tuple, value);
                }
            }
            return;
        } else if (command.equals(Commands.DO_DISCOVER_ISL_P2_COMMAND.name())) {
            discoverIslPartTwo(tuple, (IslInfoData) tuple.getValueByField(TupleFields.DATA.name()));
            return;
        }

        CommandData data = (CommandData) tuple.getValueByField(TupleFields.DATA.name());
        if (command.equals(Commands.DO_DELETE_FLOW.name())) {

        } else if (command.equals(Commands.DO_DISCOVER_ISL_COMMAND.name())) {
            discoverIsl(tuple, (DiscoverIslCommandData) data);
        } else if (command.equals(Commands.DO_DISCOVER_PATH_COMMAND.name())) {

        } else if (command.equals(Commands.DO_GET_FLOWS.name())) {

        } else if (command.equals(Commands.DO_GET_PORT_STATS.name())) {

        } else if (command.equals(Commands.DO_GET_PORT_STATUS.name())) {

        } else if (command.equals(Commands.DO_GET_SWITCH_STATUS.name())) {

        } else if (command.equals(Commands.DO_INSTALL_EGRESS_FLOW.name())) {

        } else if (command.equals(Commands.DO_INSTALL_INGRESS_FLOW.name())) {

        } else if (command.equals(Commands.DO_INSTALL_ONESWITCH_FLOW.name())) {

        } else if (command.equals(Commands.DO_INSTALL_TRANSIT_FLOW.name())) {

        } else if (command.equals(Commands.DO_PORT_MOD.name())) {

        } else if (command.equals(Commands.DO_REMOVE_SWITCH.name())) {

        } else if (command.equals(Commands.DO_SWITCH_MOD.name())) {

        } else {
            logger.error("Unknown switch command: {}".format(command));
            return;
        }

        if (values.size() > 0) {
            for (Values value : values) {
                logger.debug("emitting: {}", value);
                collector.emit(SimulatorTopology.KAFKA_BOLT_STREAM, tuple, value);
            }
        }
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;

        switches = new HashMap<>();
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("got tuple: {}", tuple.toString());
        try {
            String tupleSource = tuple.getSourceComponent();

            switch (tupleSource) {
                case SimulatorTopology.COMMAND_BOLT:
                case SimulatorTopology.SWITCH_BOLT:
                    doCommand(tuple);
                    break;
                default:
                    logger.error("tuple from unknown source: {}", tupleSource);
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(SimulatorTopology.KAFKA_BOLT_STREAM, new Fields("key", "message"));
        outputFieldsDeclarer.declareStream(SimulatorTopology.SWITCH_BOLT_STREAM,
                new Fields("dpid", TupleFields.COMMAND.name(), TupleFields.DATA.name()));
    }
}
