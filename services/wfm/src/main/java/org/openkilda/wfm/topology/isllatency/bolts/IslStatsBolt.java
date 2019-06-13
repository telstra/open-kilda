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
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.utils.MetricFormatter;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.model.CacheEndpoint;
import org.openkilda.wfm.topology.isllatency.model.IslKey;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IslStatsBolt extends AbstractBolt {
    private final long latencyTimeoutInMilliseconds;
    private MetricFormatter metricFormatter;
    private Map<IslKey, LatencyRecord> roundTripLatencyStorage;

    public IslStatsBolt(String metricPrefix, long latencyTimeoutInMilliseconds) {
        this.metricFormatter = new MetricFormatter(metricPrefix);
        this.latencyTimeoutInMilliseconds = latencyTimeoutInMilliseconds;
    }

    @Override
    protected void init() {
        roundTripLatencyStorage = new HashMap<>();
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

    List<Object> buildTsdbTuple(IslOneWayLatency data, long latency, long timestamp) throws JsonEncodeException {
        return buildTsdbTuple(data.getSrcSwitchId(), data.getSrcPortNo(), data.getDstSwitchId(), data.getDstPortNo(),
                latency, timestamp);
    }

    private List<Object> buildTsdbTuple(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort,
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
            handleRoundTripLatencyMetric(input, timestamp, (IslRoundTripLatency) data);
        } else if (data instanceof IslOneWayLatency) {
            handleOneWayLatencyMetric(input, timestamp, (IslOneWayLatency) data);
        } else {
            unhandledInput(input);
        }
    }

    private void handleRoundTripLatencyMetric(Tuple input, long timestamp, IslRoundTripLatency data)
            throws JsonEncodeException, PipelineException {
        CacheEndpoint destination = pullValue(input, CACHE_DATA_FIELD, CacheEndpoint.class);

        IslKey islKey = new IslKey(data, destination);
        roundTripLatencyStorage.put(islKey, new LatencyRecord(data.getLatency(), timestamp));

        List<Object> results = buildTsdbTuple(
                data.getSrcSwitchId(), data.getSrcPortNo(), destination.getSwitchId(), destination.getPort(),
                data.getLatency(), timestamp);
        emit(input, results);
    }

    private void handleOneWayLatencyMetric(Tuple input, long timestamp, IslOneWayLatency data)
            throws JsonEncodeException {
        IslKey forward = new IslKey(data);
        if (checkStorageAndEmitIfNeeded(input, timestamp, data, forward)) {
            return;
        }

        IslKey reverse = forward.getReverse();
        if (checkStorageAndEmitIfNeeded(input, timestamp, data, reverse)) {
            return;
        }

        emitLatency(data.getLatency(), input, timestamp, data);
    }

    private boolean checkStorageAndEmitIfNeeded(Tuple input, long timestamp, IslOneWayLatency data, IslKey key)
            throws JsonEncodeException {
        if (roundTripLatencyStorage.containsKey(key)) {
            LatencyRecord record = roundTripLatencyStorage.get(key);
            if (isRecordStillValid(record)) {
                emitLatency(record.getLatency(), input, timestamp, data);
                return true;
            }
        }
        return false;
    }

    private void emitLatency(long latency, Tuple input, long timestamp, IslOneWayLatency data)
            throws JsonEncodeException {
        List<Object> tsdbTuple = buildTsdbTuple(data, latency, timestamp);
        emit(input, tsdbTuple);
    }

    private boolean isRecordStillValid(LatencyRecord record) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - record.getTimestamp()) <= latencyTimeoutInMilliseconds;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
