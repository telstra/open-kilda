/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.portstate.bolt;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.error.MessageException;
import org.openkilda.wfm.topology.portstate.PortStateTopology;
import org.openkilda.wfm.topology.utils.AbstractKafkaParserBolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TopoDiscoParseBolt extends AbstractKafkaParserBolt {
    private static final Logger logger = LoggerFactory.getLogger(TopoDiscoParseBolt.class);
    public static final String TOPO_TO_PORT_INFO_STREAM = "parse.port.info.stream";
    public static final String FIELD_NAME = PortInfoData.class.getSimpleName();

    @Override
    public void execute(Tuple tuple) {
        switch (tuple.getSourceComponent()) {
            case PortStateTopology.TOPO_DISCO_SPOUT:
                doParseMessage(tuple);
                break;
            default:
                collector.ack(tuple);
        }
    }

    private void doParseMessage(Tuple tuple) {
        try {
            InfoData infoData = getInfoData(tuple);
            if (infoData instanceof PortInfoData) {
                collector.emit(TOPO_TO_PORT_INFO_STREAM, new Values((PortInfoData) infoData));
            }
        } catch (IOException e) {
            logger.error("Error processing: {}", tuple.toString(), e);
        } catch (MessageException e) {
            // don't really have to do anything but exception is thrown so catch it
        } finally {
            collector.ack(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(TOPO_TO_PORT_INFO_STREAM, new Fields(FIELD_NAME));
    }
}
