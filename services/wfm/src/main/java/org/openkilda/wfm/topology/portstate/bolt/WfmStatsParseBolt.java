package org.openkilda.wfm.topology.portstate.bolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.stats.SwitchPortStatusData;
import org.openkilda.wfm.topology.MessageException;
import org.openkilda.wfm.topology.utils.AbstractKafkaParserBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WfmStatsParseBolt extends AbstractKafkaParserBolt {
    private static final Logger logger = LoggerFactory.getLogger(WfmStatsParseBolt.class);
    public static final String WFM_TO_PARSE_PORT_INFO_STREAM = "wfm.to.parse.port.info.stream";

    @Override
    public void execute(Tuple tuple) {
        logger.debug("Ingoing tuple: {}", tuple);
        String request = tuple.getString(0);
        try {
            InfoData data = getInfoData(tuple);
            if (data instanceof SwitchPortStatusData) {
                doParseSwitchPortsData((SwitchPortStatusData) data);
            }
        } catch (MessageException e) {
            logger.error("Not an InfoMessage in queue message={}", request);
        } catch (IOException exception) {
            logger.error("Could not deserialize message={} exception={}", request,
                    exception.getMessage());
        } finally {
            collector.ack(tuple);
            logger.debug("Message ack: {}", request);
        }
    }

    private void doParseSwitchPortsData(SwitchPortStatusData data) {
        data.getPorts()
                .stream()
                .forEach(port -> collector.emit(WFM_TO_PARSE_PORT_INFO_STREAM, new Values(
                        new PortInfoData(data.getSwitchId(), port.getId(), port.getStatus()))));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(WFM_TO_PARSE_PORT_INFO_STREAM, new Fields(TopoDiscoParseBolt.FIELD_NAME));
    }
}
