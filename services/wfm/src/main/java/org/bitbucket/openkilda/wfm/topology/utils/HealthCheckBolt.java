package org.bitbucket.openkilda.wfm.topology.utils;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.ServiceType;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.discovery.HealthCheckInfoData;

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

public class HealthCheckBolt extends BaseRichBolt {
    private static final Logger logger = LogManager.getLogger(HealthCheckBolt.class);

    private final HealthCheckInfoData healthCheck;

    private OutputCollector collector;

    public HealthCheckBolt(String service) {
        healthCheck = new HealthCheckInfoData(service, Utils.HEALTH_CHECK_OPERATIONAL_STATUS);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        String request = input.getString(0);
        try {
            Message message = MAPPER.readValue(request, Message.class);
            if (message instanceof CommandMessage) {
                Values values = new Values(Utils.MAPPER.writeValueAsString(new InfoMessage(healthCheck,
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.NORTHBOUND)));
                collector.emit(Topic.HEALTH_CHECK.getId(), input, values);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message: ", request, exception);
        } finally {
            collector.ack(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(Topic.HEALTH_CHECK.getId(), fieldMessage);
    }
}
