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

/**
 * OFESwitchBolt - OpenFlow Event for Switches Bolt
 * <p>
 * Progress:
 * - Initial implementation will just emit the event and place on the configured topic.
 * - Future implementation may introduce flapping controls and/or other logic surrounding
 * switch up/down event.
 */
public class OFESwitchBolt extends BaseStatefulBolt<KeyValueState<String, String>> {

    private static Logger logger = LogManager.getLogger(OFESwitchBolt.class);
    /** The ID of the Stream that this bolt will emit */
    public String outputStreamId = "kilda.wfm.topo.updown";
    protected OutputCollector collector;
    protected KeyValueState<String, String> state;

    public OFESwitchBolt withOutputStreamId(String outputStreamId) {
        this.outputStreamId = outputStreamId;
        return this;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void initState(KeyValueState<String, String> state) {
        this.state = state;
    }

    /**
     * We expect to receive Switch up/down events. We'll track the Switches we've seen initially.
     * <p>
     * This bolt will listen to the splitter kafka stream.
     */
    @Override
    public void execute(Tuple tuple) {
        try {
            String json = tuple.getString(0);
            Map<String, ?> data = OFEMessageUtils.getData(json);
            String switchID = (String) data.get(OFEMessageUtils.FIELD_SWITCH_ID);
            String updown = (String) data.get(OFEMessageUtils.FIELD_STATE);
            if (switchID == null || switchID.length() == 0) {
                logger.error("OFESwitchBolt received a null/zero switch id: {}", json);
            }
            String history = state.get(switchID);
            if (history == null) {
                logger.debug("NEW SWITCH: {}, state: {}", switchID, updown);
                state.put(switchID, switchID);
            } else {
                logger.debug("OLD SWITCH: {}, state: {}", switchID, updown);
            }
            // NB: The KafkaBolt will pickup the first 2 fields, and the LinkBolt the last two
            Values dataVal = new Values(PAYLOAD, json, switchID, updown);
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
                , OFEMessageUtils.FIELD_STATE));
    }

}
