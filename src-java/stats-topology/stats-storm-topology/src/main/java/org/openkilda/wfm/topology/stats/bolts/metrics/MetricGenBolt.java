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
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class MetricGenBolt extends AbstractBolt {
    private MetricFormatter metricFormatter;

    public MetricGenBolt(String metricPrefix) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    public MetricGenBolt(String metricPrefix, String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
        this.metricFormatter = new MetricFormatter(metricPrefix);
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

    void emitMetric(String metric, long timestamp, Number value, Map<String, String> tag) {
        try {
            getOutput().emit(tuple(metricFormatter.format(metric), timestamp, value, tag));
        } catch (JsonEncodeException e) {
            log.error("Error during serialization of datapoint", e);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(KafkaRecordTranslator.FIELD_ID_PAYLOAD));
    }
}
