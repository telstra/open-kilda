package org.bitbucket.openkilda.wfm.topology.stats.metrics;

import org.apache.storm.tuple.Tuple;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsData;
import org.bitbucket.openkilda.wfm.topology.stats.StatsComponentType;
import org.bitbucket.openkilda.wfm.topology.stats.StatsStreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

public class FlowMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(FlowMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Flow stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        FlowStatsData data = (FlowStatsData) message.getData();
        long timestamp = message.getTimestamp();
        data.getStats().forEach(stats -> stats.getEntries().forEach(entry -> {
            Map<String, String> tags = new HashMap<>();
            tags.put("switchid", data.getSwitchId().replaceAll(":", ""));
            tags.put("cookie", String.valueOf(entry.getCookie()));
            collector.emit(tuple("pen.flow.tableid", timestamp, entry.getTableId(), tags));
            collector.emit(tuple("pen.flow.packets", timestamp, entry.getPacketCount(), tags));
            collector.emit(tuple("pen.flow.bytes", timestamp, entry.getByteCount(), tags));
            collector.emit(tuple("pen.flow.bits", timestamp, entry.getByteCount()*8, tags));
        }));
        collector.ack(input);
    }
}
