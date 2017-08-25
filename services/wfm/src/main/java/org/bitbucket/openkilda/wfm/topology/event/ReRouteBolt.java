package org.bitbucket.openkilda.wfm.topology.event;

import static org.bitbucket.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowReroute;
import org.bitbucket.openkilda.wfm.OFEMessageUtils;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

/**
 * Events Correlation Bolt. Processes events and implements flow path recomputation logic. TODO: implement flow path
 * recomputation logic
 */
public class ReRouteBolt extends BaseStatefulBolt<KeyValueState<String, String>> {
    public static final String DEFAULT_OUTPUT_STREAM_ID = "kilda.wfm.tpe.flow";
    private static final Logger logger = LogManager.getLogger(ReRouteBolt.class);

    private String outputStreamId = DEFAULT_OUTPUT_STREAM_ID;
    private OutputCollector collector;
    private KeyValueState<String, String> state;

    public ReRouteBolt withOutputStreamId(String outputStreamId) {
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, String> state) {
        this.state = state;
    }

    @Override
    public void execute(Tuple tuple) {
        String componentId = tuple.getSourceComponent();
        String streamId = tuple.getSourceStreamId();

        try {
            if (componentId.startsWith(InfoEventSplitterBolt.I_SWITCH_UPDOWN)) {
                handleSwitchEvent(tuple);
            } else if (componentId.startsWith(InfoEventSplitterBolt.I_PORT_UPDOWN)) {
                handlePortEvent(tuple);
            } else if (componentId.startsWith(InfoEventSplitterBolt.I_ISL_UPDOWN)) {
                handleLinkEvent(tuple);
            } else {
                logger.error("Unknown component: component={}, stream={}, tuple={}",
                        componentId, streamId, tuple);
            }
        } catch (JsonProcessingException exception) {
            logger.error("Could not serialize message: component={}, stream={}, tuple={}",
                    componentId, streamId, tuple);
        } finally {
            logger.debug("Event Correlation ack: component={}, stream={}", componentId, streamId);
            collector.ack(tuple);
        }
    }

    private void handleSwitchEvent(Tuple tuple) throws JsonProcessingException {
        String switchId = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String state = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);

        if (OFEMessageUtils.SWITCH_DOWN.equals(state)) {
            CommandData data = new FlowReroute(null, switchId, 0);
            Message message = new CommandMessage(data, System.currentTimeMillis(),
                    DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
            Values values = new Values(MAPPER.writeValueAsString(message));
            collector.emit(outputStreamId, tuple, values);
        }
    }

    private void handlePortEvent(Tuple tuple) throws JsonProcessingException {
        String switchId = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String portId = tuple.getStringByField(OFEMessageUtils.FIELD_PORT_ID);
        String state = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);

        if (OFEMessageUtils.PORT_DOWN.equals(state)) {
            CommandData data = new FlowReroute(null, switchId, Integer.parseInt(portId));
            Message message = new CommandMessage(data, System.currentTimeMillis(),
                    DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
            Values values = new Values(MAPPER.writeValueAsString(message));
            collector.emit(outputStreamId, tuple, values);
        }
    }

    private void handleLinkEvent(Tuple tuple) throws JsonProcessingException {
        String switchId = tuple.getStringByField(OFEMessageUtils.FIELD_SWITCH_ID);
        String portId = tuple.getStringByField(OFEMessageUtils.FIELD_PORT_ID);
        String state = tuple.getStringByField(OFEMessageUtils.FIELD_STATE);

        if (OFEMessageUtils.LINK_DOWN.equals(state)) {
            CommandData data = new FlowReroute(null, switchId, Integer.parseInt(portId));
            Message message = new CommandMessage(data, System.currentTimeMillis(),
                    DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
            Values values = new Values(MAPPER.writeValueAsString(message));
            collector.emit(outputStreamId, tuple, values);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, AbstractTopology.fieldMessage);
    }
}
