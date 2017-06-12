package org.bitbucket.openkilda.wfm.topology.stats.bolts;

import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsData;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsData;
import org.bitbucket.openkilda.wfm.topology.stats.StatsStreamType;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class SpeakerBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerBolt.class);
    private static final String PORT_STATS_STREAM = StatsStreamType.PORT_STATS.toString();
    private static final String METER_CFG_STATS_STREAM = StatsStreamType.METER_CONFIG_STATS.toString();
    private static final String FLOW_STATS_STREAM = StatsStreamType.FLOW_STATS.toString();

    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);
        String request = tuple.getString(0);
        //String request = tuple.getStringByField("value");
        try {
            Message stats = Utils.MAPPER.readValue(request, Message.class);
            if (!Destination.WFM_STATS.equals(stats.getDestination()) || !(stats instanceof InfoMessage)) {
                return;
            }
            InfoMessage message = (InfoMessage) stats;
            final InfoData data = message.getData();
            if (data instanceof PortStatsData) {
                logger.debug("Port stats message: {}", new Values(request));
                outputCollector.emit(PORT_STATS_STREAM, tuple, new Values(message));
            } else if (data instanceof MeterConfigStatsData) {
                logger.debug("Meter config stats message: {}", new Values(request));
                outputCollector.emit(METER_CFG_STATS_STREAM, tuple, new Values(message));
            } else if (data instanceof FlowStatsData) {
                logger.debug("Flow stats message: {}", new Values(request));
                outputCollector.emit(FLOW_STATS_STREAM, tuple, new Values(message));
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message={}", request, exception);
        } finally {
            outputCollector.ack(tuple);
            logger.debug("Message ack: {}", request);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(PORT_STATS_STREAM, fieldMessage);
        outputFieldsDeclarer.declareStream(METER_CFG_STATS_STREAM, fieldMessage);
        outputFieldsDeclarer.declareStream(FLOW_STATS_STREAM, fieldMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }
}
