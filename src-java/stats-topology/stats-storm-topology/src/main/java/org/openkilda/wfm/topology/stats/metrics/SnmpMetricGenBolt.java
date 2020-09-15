/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.metrics;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.snmp.metric.SnmpMetricData;
import org.openkilda.messaging.snmp.metric.SnmpMetricPoint;
import org.openkilda.wfm.share.utils.MetricFormatter;

import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;

public class SnmpMetricGenBolt extends MetricGenBolt {

    private MetricFormatter metricFormatter;

    public SnmpMetricGenBolt(String metricPrefix) {
        super(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        InfoMessage message = (InfoMessage) input.getValueByField(MESSAGE_FIELD);
        SnmpMetricData metricData = (SnmpMetricData) message.getData();

        long timestamp = message.getTimestamp();
        String metricName = metricData.getMetricName();
        Map<String, String> tags = metricData.getTags();

        for (SnmpMetricPoint entry : metricData.getValues()) {
            // the tags from metricData includes switchid with key SnmpMetricData.SWITCH_ID_TAG_KEY
            Map<String, String> consolidatedTags = new HashMap<>(tags);
            consolidatedTags.put("port", String.valueOf(entry.getPort()));
            if (entry.getSubIndex() != null) {
                consolidatedTags.put("subIndex", String.valueOf(entry.getSubIndex()));
            }

            emitMetric(metricName, timestamp, entry.getValue(), consolidatedTags);
        }
    }
}
