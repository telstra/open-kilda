/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.isllatency.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IslStatsBolt extends AbstractBolt {
    private MetricFormatter metricFormatter;

    public IslStatsBolt(String metricPrefix) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    private static List<Object> tsdbTuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws JsonEncodeException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        try {
            return Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(datapoint, e);
        }
    }

    @VisibleForTesting
    List<Object> buildTsdbTuple(IslInfoData data, long timestamp) throws JsonEncodeException {
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", data.getSource().getSwitchId().toOtsdFormat());
        tags.put("src_port", String.valueOf(data.getSource().getPortNo()));
        tags.put("dst_switch", data.getDestination().getSwitchId().toOtsdFormat());
        tags.put("dst_port", String.valueOf(data.getDestination().getPortNo()));

        return tsdbTuple(metricFormatter.format("isl.latency"), timestamp, data.getLatency(), tags);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        Message message = pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof IslInfoData) {
                handleIslInfoData(input, message, (IslInfoData) data);
            } else {
                unhandledInput(input);
            }
        } else {
            unhandledInput(input);
        }
    }

    private void handleIslInfoData(Tuple input, Message message, IslInfoData data) throws JsonEncodeException {
        List<Object> results = buildTsdbTuple(data, message.getTimestamp());
        getOutput().emit(input, results);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
