package org.bitbucket.openkilda.wfm.topology.cache;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.discovery.DumpNetwork;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;

public class RequesterBolt extends BaseRichBolt {
    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(RequesterBolt.class);

    /**
     * Output collector.
     */
    private OutputCollector outputCollector;

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
        requestNetworkDump();
    }

    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);
    }

    private void requestNetworkDump() {
        CommandMessage command = new CommandMessage(new DumpNetwork(),
                System.currentTimeMillis(), Utils.DEFAULT_CORRELATION_ID, Destination.TOPOLOGY_ENGINE);
        try {
            Values values = new Values(Utils.MAPPER.writeValueAsString(command));
            outputCollector.emit(StreamType.CACHE_TPE.toString(), values);
        } catch (IOException exception) {
            logger.error("Could not send dump network request: {}", command, exception);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(StreamType.CACHE_TPE.toString(), CacheTopology.fieldMessage);
    }
}
