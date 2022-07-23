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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.portstate.PortStateTopology;
import org.openkilda.wfm.topology.portstate.exceptions.MessageException;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class TopoDiscoParseBolt extends AbstractKafkaParserBolt {
    public static final String TOPO_TO_PORT_INFO_STREAM = "parse.port.info.stream";
    public static final String FIELD_NAME = PortInfoData.class.getSimpleName();

    public TopoDiscoParseBolt(String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
    }

    @Override
    public void handleInput(Tuple tuple) {
        if (active) {
            switch (tuple.getSourceComponent()) {
                case PortStateTopology.TOPO_DISCO_SPOUT:
                    doParseMessage(tuple);
                    break;
                default:
                    unhandledInput(tuple);
            }
        }
    }

    private void doParseMessage(Tuple tuple) {
        Message message = (Message) tuple.getValueByField(MessageKafkaTranslator.FIELD_ID_PAYLOAD);
        try {
            InfoData infoData = getInfoData(message);
            if (infoData instanceof PortInfoData) {
                emit(TOPO_TO_PORT_INFO_STREAM, new Values(infoData));
            }
        } catch (MessageException e) {
            log.error("Not an InfoMessage in queue message={}", message);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declareStream(TOPO_TO_PORT_INFO_STREAM, new Fields(FIELD_NAME));
        outputFieldsDeclarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
