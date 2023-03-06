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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.service.TimeSeriesMeterEmitter;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class MetricGenBolt extends AbstractBolt implements TimeSeriesMeterEmitter {
    private MetricFormatter metricFormatter;

    public MetricGenBolt(String metricPrefix) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    public MetricGenBolt(String metricPrefix, String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    @Override
    public void emitPacketAndBytePoints(
            MetricFormatter formatter, long timestamp, long packetCount, long byteCount, Map<String, String> tags) {
        emitMetric(formatter.format("packets"), timestamp, packetCount, tags);
        emitMetric(formatter.format("bytes"), timestamp, byteCount, tags);
        emitMetric(formatter.format("bits"), timestamp, byteCount * 8, tags);
    }

    void emitMetric(String metric, long timestamp, Number value, Map<String, String> tag) {
        try {
            String formattedMetric = metricFormatter.format(metric);
            log.trace(
                    "Emit stats metric point: timestamp={}, metric={}, value={}, tags={}",
                    timestamp, formattedMetric, value, tag);
            getOutput().emit(tuple(formattedMetric, timestamp, value, tag));
        } catch (JsonEncodeException e) {
            log.error("Error during serialization of datapoint", e);
        }
    }

    protected static List<Object> tuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws JsonEncodeException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        String json;
        try {
            json = Utils.MAPPER.writeValueAsString(datapoint);
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(datapoint, e);
        }
        return Collections.singletonList(json);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(KafkaRecordTranslator.FIELD_ID_PAYLOAD));
    }
}
