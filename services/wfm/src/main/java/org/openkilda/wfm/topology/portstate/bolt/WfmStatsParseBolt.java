package org.openkilda.wfm.topology.portstate.bolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.discovery.SwitchPortsData;
import org.openkilda.wfm.topology.MessageException;
import org.openkilda.wfm.topology.utils.AbstractKafkaParserBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WfmStatsParseBolt extends AbstractKafkaParserBolt {
    private static final Logger logger = LoggerFactory.getLogger(WfmStatsParseBolt.class);
    private static final String WFM_STATS_PARSE_STREAM = "wfm.stats.parse.stream";
    protected static final String WFM_TO_PARSE_PORT_INFO_STREAM = "wfm.to.parse.port.info.stream";

    @Override
    public void execute(Tuple tuple) {
        if (tuple.getSourceStreamId().equals(WFM_STATS_PARSE_STREAM)) {
            try {
                InfoData data = getInfoData(tuple);
                if (data instanceof SwitchPortsData) {
                    doParseSwitchPortsData((SwitchPortsData) data);
                }
            } catch (IOException e) {
                logger.error("Error parsing: {}", tuple.toString(), e);
            } catch (MessageException e) {
                //Nothing much to do here
            }
        }
        collector.ack(tuple);
    }

    private void doParseSwitchPortsData(SwitchPortsData data) {
        data.getPorts()
                .stream()
                .forEach(port -> collector.emit(new Values(port)));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(WFM_TO_PARSE_PORT_INFO_STREAM, new Fields(TopoDiscoParseBolt.FIELD_NAME));
    }
}
