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

package org.openkilda.wfm.topology.isllatency.service;

import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.isllatency.model.IslKey;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;

public class IslStatsService {
    private final IslStatsCarrier carrier;
    private final long latencyTimeoutInMilliseconds;
    private Map<IslKey, LatencyRecord> roundTripLatencyStorage;

    public IslStatsService(IslStatsCarrier carrier, long latencyTimeoutInMilliseconds) {
        this.carrier = carrier;
        this.latencyTimeoutInMilliseconds = latencyTimeoutInMilliseconds;
        roundTripLatencyStorage = new HashMap<>();
    }

    /**
     * Handle round trip latency metric.
     *
     * @param input tuple
     * @param timestamp timestamp of metric
     * @param data round trip latency info data
     * @param destination isl destination endpoint
     */
    public void handleRoundTripLatencyMetric(
            Tuple input, long timestamp, IslRoundTripLatency data, Endpoint destination) {
        IslKey islKey = new IslKey(data, destination);
        roundTripLatencyStorage.put(islKey, new LatencyRecord(data.getLatency(), timestamp));

        carrier.emitLatency(
                input,
                data.getSrcSwitchId(),
                data.getSrcPortNo(),
                destination.getDatapath(),
                destination.getPortNumber(),
                data.getLatency(),
                timestamp);
    }

    /**
     * Handle one way latency metric.
     *
     * @param input tuple
     * @param timestamp timestamp of metric
     * @param data isl one way latency info data
     */
    public void handleOneWayLatencyMetric(Tuple input, long timestamp, IslOneWayLatency data) {
        IslKey forward = new IslKey(data);
        if (checkStorageAndEmitIfNeeded(input, timestamp, data, forward)) {
            return;
        }

        IslKey reverse = forward.getReverse();
        if (checkStorageAndEmitIfNeeded(input, timestamp, data, reverse)) {
            return;
        }

        carrier.emitLatency(
                input,
                data.getSrcSwitchId(),
                data.getSrcPortNo(),
                data.getDstSwitchId(),
                data.getDstPortNo(),
                data.getLatency(),
                timestamp);
    }

    private boolean checkStorageAndEmitIfNeeded(Tuple input, long timestamp, IslOneWayLatency data, IslKey key) {
        if (roundTripLatencyStorage.containsKey(key)) {
            LatencyRecord record = roundTripLatencyStorage.get(key);
            if (isRecordStillValid(record)) {

                carrier.emitLatency(
                        input,
                        data.getSrcSwitchId(),
                        data.getSrcPortNo(),
                        data.getDstSwitchId(),
                        data.getDstPortNo(),
                        record.getLatency(),
                        timestamp);
                return true;
            }
        }
        return false;
    }

    private boolean isRecordStillValid(LatencyRecord record) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - record.getTimestamp()) <= latencyTimeoutInMilliseconds;
    }
}
