package org.bitbucket.openkilda.wfm.topology.stats.metrics;

import org.apache.storm.tuple.Tuple;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsData;
import org.bitbucket.openkilda.wfm.topology.stats.StatsComponentType;
import org.bitbucket.openkilda.wfm.topology.stats.StatsStreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

public class PortMetricGenBolt extends MetricGenBolt {
    private static final Logger logger = LoggerFactory.getLogger(PortMetricGenBolt.class);

    @Override
    public void execute(Tuple input) {
        StatsComponentType componentId = StatsComponentType.valueOf(input.getSourceComponent());
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);

        if (!Destination.WFM_STATS.equals(message.getDestination())) {
            collector.ack(input);
            return;
        }

        logger.debug("Port stats message: {}={}, component={}, stream={}",
                CORRELATION_ID, message.getCorrelationId(), componentId, StatsStreamType.valueOf(input.getSourceStreamId()));
        PortStatsData data = (PortStatsData) message.getData();
        long timestamp = message.getTimestamp();
        data.getStats().forEach(stats -> stats.getEntries().forEach(entry -> {
            Map<String, String> tags = new HashMap<>();
            tags.put("switch.id", data.getSwitchId().replaceAll(":", ""));
            tags.put("port.id", String.valueOf(entry.getPortNo()));
            collector.emit(tuple("rx-packets", timestamp, entry.getRxPackets(), tags));
            collector.emit(tuple("tx-packets", timestamp, entry.getTxPackets(), tags));
            collector.emit(tuple("rx-bytes", timestamp, entry.getRxBytes(), tags));
            collector.emit(tuple("tx-bytes", timestamp, entry.getTxBytes(), tags));
            collector.emit(tuple("rx-dropped", timestamp, entry.getRxDropped(), tags));
            collector.emit(tuple("tx-dropped", timestamp, entry.getTxDropped(), tags));
            collector.emit(tuple("rx-errors", timestamp, entry.getRxErrors(), tags));
            collector.emit(tuple("tx-errors", timestamp, entry.getTxErrors(), tags));
            collector.emit(tuple("rx-frame-err", timestamp, entry.getRxFrameErr(), tags));
            collector.emit(tuple("rx-over-err", timestamp, entry.getRxOverErr(), tags));
            collector.emit(tuple("rx-crc-err", timestamp, entry.getRxCrcErr(), tags));
            collector.emit(tuple("collisions", timestamp, entry.getCollisions(), tags));
        }));
        collector.ack(input);
    }
}
