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

import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.CACHE_BOLT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.CACHE_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_STATUS_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_STATUS_UPDATE_BOLT_ID;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ONE_WAY_MANIPULATION_BOLT_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.Datapoint;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.isllatency.carriers.IslStatsCarrier;
import org.openkilda.wfm.topology.isllatency.service.IslStatsService;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IslStatsBolt extends AbstractBolt implements IslStatsCarrier {
    public static final String LATENCY_METRIC_NAME = "isl.rtt";
    public static final Fields OUTGOING_STREAM_FIELDS = new Fields(KafkaRecordTranslator.FIELD_ID_PAYLOAD);

    private transient IslStatsService islStatsService;
    private final long latencyTimeout;
    private MetricFormatter metricFormatter;

    public IslStatsBolt(String metricPrefix, long latencyTimeout) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
        this.latencyTimeout = latencyTimeout;
    }

    @Override
    protected void init() {
        islStatsService = new IslStatsService(this, latencyTimeout);
    }

    private static List<Object> tsdbTuple(String metric, long timestamp, Number value, Map<String, String> tag)
            throws JsonProcessingException {
        Datapoint datapoint = new Datapoint(metric, timestamp, tag, value);
        return Collections.singletonList(Utils.MAPPER.writeValueAsString(datapoint));
    }

    @VisibleForTesting
    List<Object> buildTsdbTuple(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort,
                                long latency, long timestamp, String origin) throws JsonProcessingException {
        Map<String, String> tags = new HashMap<>();
        tags.put("src_switch", srcSwitchId.toOtsdFormat());
        tags.put("src_port", String.valueOf(srcPort));
        tags.put("dst_switch", dstSwitchId.toOtsdFormat());
        tags.put("dst_port", String.valueOf(dstPort));
        tags.put("origin", origin);

        return tsdbTuple(metricFormatter.format(LATENCY_METRIC_NAME), timestamp, latency, tags);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (ISL_STATUS_UPDATE_BOLT_ID.equals(input.getSourceComponent())) {
            handleStatusUpdate(input);
        } else if (ONE_WAY_MANIPULATION_BOLT_ID.equals(input.getSourceComponent())
                || CACHE_BOLT_ID.equals(input.getSourceComponent())) {
            handleLatencyData(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleLatencyData(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, LATENCY_DATA_FIELD, InfoData.class);
        long timestamp = getCommandContext().getCreateTime();

        if (data instanceof IslRoundTripLatency) {
            Endpoint destination = pullValue(input, CACHE_DATA_FIELD, Endpoint.class);
            islStatsService.handleRoundTripLatencyMetric(timestamp, (IslRoundTripLatency) data, destination);
        } else if (data instanceof IslOneWayLatency) {
            islStatsService.handleOneWayLatencyMetric(timestamp, (IslOneWayLatency) data);
        } else {
            unhandledInput(input);
        }
    }

    private void handleStatusUpdate(Tuple tuple) throws PipelineException {
        IslStatusUpdateNotification notification = pullValue(
                tuple, ISL_STATUS_FIELD, IslStatusUpdateNotification.class);
        islStatsService.handleIstStatusUpdateNotification(notification);
    }

    @Override
    public void emitLatency(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort,
                            long latency, long timestamp, String origin) {
        List<Object> tsdbTuple;
        try {
            tsdbTuple = buildTsdbTuple(srcSwitch, srcPort, dstSwitch, dstPort, latency, timestamp, origin);
        } catch (JsonProcessingException e) {
            log.error(String.format("Couldn't create OpenTSDB tuple: %s", e.getMessage()), e);
            return;
        }
        emit(getCurrentTuple(), tsdbTuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(OUTGOING_STREAM_FIELDS);
    }
}
