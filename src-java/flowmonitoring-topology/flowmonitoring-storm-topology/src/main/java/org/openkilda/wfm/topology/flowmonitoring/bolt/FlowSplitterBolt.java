/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.bolt;

import static org.openkilda.wfm.topology.flowmonitoring.FlowMonitoringTopology.Stream.FLOW_UPDATE_STREAM_ID;
import static org.openkilda.wfm.topology.flowmonitoring.bolt.FlowCacheBolt.FLOW_ID_FIELD;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowSplitterBolt extends AbstractBolt {

    public static final String INFO_DATA_FIELD = "info-data-field";

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        InfoData payload = pullValue(input, FIELD_ID_PAYLOAD, InfoMessage.class).getData();
        if (payload instanceof FlowRttStatsData) {
            FlowRttStatsData flowRttStatsData = (FlowRttStatsData) payload;
            emit(input, new Values(flowRttStatsData.getFlowId(), flowRttStatsData, getCommandContext()));
        } else if (payload instanceof UpdateFlowInfo) {
            UpdateFlowInfo updateFlowInfo = (UpdateFlowInfo) payload;
            emit(FLOW_UPDATE_STREAM_ID.name(), input, new Values(updateFlowInfo.getFlowId(), updateFlowInfo,
                    getCommandContext()));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(FLOW_ID_FIELD, INFO_DATA_FIELD, FIELD_ID_CONTEXT));
        declarer.declareStream(FLOW_UPDATE_STREAM_ID.name(),
                new Fields(FLOW_ID_FIELD, INFO_DATA_FIELD, FIELD_ID_CONTEXT));
    }
}
