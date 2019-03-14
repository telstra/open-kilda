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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Map;

public class StatsProducer extends Abstract {
    public static final String BOLT_ID = ComponentId.STATS_PRODUCER.toString();

    public static final String FIELD_ID_STATS_DATAPOINT = OtsdbEncoder.FIELD_ID_STATS_DATAPOINT;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_STATS_DATAPOINT, FIELD_ID_CONTEXT);

    private MetricFormatter metricFormatter;

    public StatsProducer(String metricPrefix) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        PingContext pingContext = pullPingContext(input);

        HashMap<String, String> tags = new HashMap<>();
        tags.put("flowid", pingContext.getFlowId());

        produceMetersStats(input, tags, pingContext);
    }

    private void produceMetersStats(Tuple input, Map<String, String> tags, PingContext pingContext)
            throws PipelineException {
        tags.put("direction", pingContext.getDirection().name().toLowerCase());

        PingMeters meters = pingContext.getMeters();
        Datapoint datapoint = new Datapoint(
                metricFormatter.format("flow.latency"), pingContext.getTimestamp(), tags, meters.getNetworkLatency());
        emit(input, datapoint);
    }

    private void emit(Tuple input, Datapoint datapoint) throws PipelineException {
        Values output = new Values(datapoint, pullContext(input));
        getOutput().emit(input, output);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
