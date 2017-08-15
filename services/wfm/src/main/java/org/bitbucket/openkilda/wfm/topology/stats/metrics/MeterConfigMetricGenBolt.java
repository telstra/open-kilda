package org.bitbucket.openkilda.wfm.topology.stats.metrics;

import org.apache.storm.tuple.Tuple;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.bitbucket.openkilda.wfm.topology.stats.StatsComponentType;
import org.bitbucket.openkilda.wfm.topology.stats.StatsStreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

public class MeterConfigMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(MeterConfigMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Meter config stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        MeterConfigStatsData data = (MeterConfigStatsData) message.getData();
        long timestamp = message.getTimestamp();
        data.getStats().forEach(stats -> stats.getMeterIds().forEach(meterId -> {
            Map<String, String> tags = new HashMap<>();
            tags.put("switchid", data.getSwitchId().replaceAll(":", ""));
            tags.put("meterid", meterId.toString());
            collector.emit(tuple("pen.switch.meters", timestamp, meterId, tags));
        }));
        collector.ack(input);
    }
}
