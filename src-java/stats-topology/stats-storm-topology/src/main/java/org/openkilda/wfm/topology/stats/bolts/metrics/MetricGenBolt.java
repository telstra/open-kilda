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

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.stats.service.TimeSeriesMeterEmitter;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

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
        String formattedMetric = metricFormatter.format(metric);
        log.trace(
                "Emit stats metric point: timestamp={}, metric={}, value={}, tags={}",
                timestamp, formattedMetric, value, tag);
        Datapoint datapoint = new Datapoint(formattedMetric, timestamp, tag, value);
        getOutput().emit(new Values(datapoint));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(KafkaRecordTranslator.FIELD_ID_PAYLOAD));
    }
}
