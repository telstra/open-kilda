package org.openkilda.simulator.bolts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.state.State;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.*;
import org.openkilda.simulator.classes.Port;
import org.openkilda.simulator.classes.Switch;
import org.openkilda.simulator.messages.LinkMessage;
import org.openkilda.simulator.messages.SwitchMessage;
import org.projectfloodlight.openflow.types.DatapathId;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SwitchBolt extends BaseStatefulBolt {
    private static final Logger logger = LogManager.getLogger(SwitchBolt.class);
    private OutputCollector collector;
    private KeyValueState<String, Switch> switches;

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

    protected void addSwitch(Tuple tuple, SwitchMessage switchMessage) throws IOException {
        Switch sw = switches.get(switchMessage.getDpid());
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

            collector.emit(tuple, new Values("INFO", makeSwitchMessage(sw, SwitchState.ADDED)));
            collector.emit(tuple, new Values("INFO", makeSwitchMessage(sw, SwitchState.ACTIVATED)));

            for (Port p : sw.getPorts()) {
                logger.debug("emitting " + makePortMessage(sw, p.getNumber(), PortChangeType.UP));
                collector.emit(tuple, new Values("INFO", makePortMessage(sw, p.getNumber(), PortChangeType.UP)));
            }
        }
    }

    protected void discoverIsl(Tuple tuple, DiscoverIslCommandData data) throws ArrayIndexOutOfBoundsException, IOException {
        Switch sw = switches.get(data.getSwitchId());
        if (sw == null) {
            return;
        }

        Port localPort = sw.getPort(data.getPortNo());
        if (localPort == null) {
            return;
        }

        if (localPort.isActiveIsl()) {
            Switch peerSw = switches.get(localPort.getPeerSwitch());
            Port peerSwitchPort = peerSw.getPort(localPort.getPeerPortNum());
            if (peerSwitchPort.isActiveIsl()) {
                List<PathNode> path = new ArrayList<>();
                PathNode path1 = new PathNode(sw.getDpid().toString(), localPort.getNumber(), 0);
                path1.setSegLatency(peerSwitchPort.getLatency());
                PathNode path2 = new PathNode(peerSw.getDpid().toString(), peerSwitchPort.getNumber(), 1);
                path.add(path1);
                path.add(path2);
                IslInfoData islInfoData = new IslInfoData(
                        peerSwitchPort.getLatency(),
                        path,
                        100000,
                        IslChangeType.DISCOVERED,
                        100000);
                long now = Instant.now().toEpochMilli();
                InfoMessage infoMessage = new InfoMessage(islInfoData, now, "system", null);
                logger.debug("emitting: {}", Utils.MAPPER.writeValueAsString(infoMessage));
                collector.emit(tuple, new Values("INFO", Utils.MAPPER.writeValueAsString(infoMessage)));
            }
        }
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            Fields fields = tuple.getFields();
            if (fields.contains("dpid")
                    && fields.contains("switch")
                    && tuple.getValueByField("switch") instanceof SwitchMessage) {
                logger.debug("received a switch: " + tuple.getValueByField("dpid"));
                SwitchMessage switchMessage = (SwitchMessage) tuple.getValueByField("switch");
                addSwitch(tuple, switchMessage);
            }
            if (fields.contains("command")) {
                Object data = tuple.getValueByField("command");
                if (data instanceof DiscoverIslCommandData) {
                    discoverIsl(tuple, (DiscoverIslCommandData) data);
                }
            }
        } catch (IOException e) {
            logger.error("Error parsing: ", e);
        } catch (Exception e) {
            logger.error(e);
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("key", "message"));
    }

    @Override
    public void initState(State state) {
        switches = (KeyValueState<String, Switch>) state;
    }
}
