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

import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.CACHE_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.TIMESTAMP_FIELD;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.service.IslStatsCarrier;
import org.openkilda.wfm.topology.isllatency.service.IslStatsService;

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
public class IslStatsBolt extends AbstractBolt implements IslStatsCarrier {
    private transient IslStatsService islStatsService;
    private final long latencyTimeoutInMilliseconds;
    private MetricFormatter metricFormatter;

    public IslStatsBolt(String metricPrefix, long latencyTimeoutInMilliseconds) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
        this.latencyTimeoutInMilliseconds = latencyTimeoutInMilliseconds;
    }

    @Override
    protected void init() {
        islStatsService = new IslStatsService(this, latencyTimeoutInMilliseconds);
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
    List<Object> buildTsdbTuple(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort,
                                long latency, long timestamp) throws JsonEncodeException {
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", srcSwitchId.toOtsdFormat());
        tags.put("src_port", String.valueOf(srcPort));
        tags.put("dst_switch", dstSwitchId.toOtsdFormat());
        tags.put("dst_port", String.valueOf(dstPort));

        return tsdbTuple(metricFormatter.format("isl.latency"), timestamp, latency, tags);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        InfoData data = pullValue(input, LATENCY_DATA_FIELD, InfoData.class);
        long timestamp = input.getLongByField(TIMESTAMP_FIELD);

        if (data instanceof IslRoundTripLatency) {
            Endpoint destination = pullValue(input, CACHE_DATA_FIELD, Endpoint.class);
            islStatsService.handleRoundTripLatencyMetric(input, timestamp, (IslRoundTripLatency) data, destination);
        } else if (data instanceof IslOneWayLatency) {
            islStatsService.handleOneWayLatencyMetric(input, timestamp, (IslOneWayLatency) data);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void emitLatency(Tuple input, SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort,
                            long latency, long timestamp) {
        List<Object> tsdbTuple;
        try {
            tsdbTuple = buildTsdbTuple(srcSwitch, srcPort, dstSwitch, dstPort, latency, timestamp);
        } catch (JsonEncodeException e) {
            log.error(String.format("Couldn't create OpenTSDB tuple: %s", e.getMessage()), e);
            return;
        }
        emit(input, tsdbTuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
