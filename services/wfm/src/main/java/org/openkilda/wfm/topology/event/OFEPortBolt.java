/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.event;

import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.wfm.OFEMessageUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.state.KeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class OFEPortBolt
        extends BaseStatefulBolt<KeyValueState<String, ConcurrentHashMap<String, String>>> {

    private static Logger logger = LogManager.getLogger(OFEPortBolt.class);
    /** The ID of the Stream that this bolt will emit */
    public String outputStreamId = "kilda.wfm.topo.updown";
    protected OutputCollector collector;
    /** SwitchID -> PortIDs */
    protected KeyValueState<String, ConcurrentHashMap<String, String>> state;

    public OFEPortBolt withOutputStreamId(String outputStreamId) {
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, ConcurrentHashMap<String, String>> state) {
        this.state = state;
    }

    @Override
    public void execute(Tuple tuple) {
        try {
            String json = tuple.getString(0);
            logger.trace("json = {}", json);

            Map<String, ?> data = OFEMessageUtils.getData(json);
            String switchID = (String) data.get(OFEMessageUtils.FIELD_SWITCH_ID);
            String portID = String.valueOf(data.get(OFEMessageUtils.FIELD_PORT_ID));
            String updown = (String) data.get(OFEMessageUtils.FIELD_STATE);
            if (switchID == null || switchID.length() == 0) {
                logger.error("OFESwitchBolt received a null/zero switch id: {}", json);
            }
            ConcurrentHashMap<String, String> history = state.get(switchID);
            if (history == null) {
                logger.debug("NEW SWITCH & PORT: {} / {}, state: {}",
                        switchID, portID, updown);
                ConcurrentHashMap<String, String> fred = new ConcurrentHashMap<>();
                fred.put(portID, portID);
                state.put(switchID, fred);
            } else {
                String portHistory = history.get(portID);
                if (portHistory == null) {
                    logger.debug("OLD SWITCH, NEW PORT: {} / {}, state: {}",
                            switchID, portID, updown);
                    history.put(portID, portID);
                } else {
                    logger.debug("OLD SWITCH & PORT: {} / {}, state: {}",
                            switchID, portID, updown);
                    // don't do anything .. yet
                }
            }
            // NB: The KafkaBolt will pickup the first 2 fields, and the LinkBolt the last two
            Values dataVal = new Values(PAYLOAD, json, switchID, portID, updown);
            collector.emit(outputStreamId, tuple, dataVal);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            collector.ack(tuple);
        }

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(outputStreamId, new Fields(
                "key", "message"
                , OFEMessageUtils.FIELD_SWITCH_ID
                , OFEMessageUtils.FIELD_PORT_ID
                , OFEMessageUtils.FIELD_STATE));
    }

}
