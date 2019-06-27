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
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.IslStatus;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.isllatency.carriers.IslStatsCarrier;
import org.openkilda.wfm.topology.isllatency.model.IslKey;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import com.google.common.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class IslStatsService {
    private final IslStatsCarrier carrier;
    private final long latencyTimeout;
    private Map<IslKey, LatencyRecord> roundTripLatencyStorage;
    private Map<IslKey, Instant> oneWayLatencyEmitTimeoutMap;
    private Map<IslKey, TreeMap<Long, Long>> oneWayLatencyStorage;


    public IslStatsService(IslStatsCarrier carrier, long latencyTimeout) {
        this.carrier = carrier;
        this.latencyTimeout = latencyTimeout;
        roundTripLatencyStorage = new HashMap<>();
        oneWayLatencyEmitTimeoutMap = new HashMap<>();
        oneWayLatencyStorage = new HashMap<>();
    }

    /**
     * Handle round trip latency metric.
     *
     * @param timestamp timestamp of metric
     * @param data round trip latency info data
     * @param destination isl destination endpoint
     */
    public void handleRoundTripLatencyMetric(long timestamp, IslRoundTripLatency data, Endpoint destination) {
        IslKey islKey = new IslKey(data, destination);
        roundTripLatencyStorage.put(islKey, new LatencyRecord(data.getLatency(), timestamp));

        oneWayLatencyEmitTimeoutMap.remove(islKey);
        oneWayLatencyEmitTimeoutMap.remove(islKey.getReverse());
        clearOneWayRecords(islKey);
        clearOneWayRecords(islKey.getReverse());

        carrier.emitLatency(
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
     * @param timestamp timestamp of metric
     * @param data isl one way latency info data
     */
    public void handleOneWayLatencyMetric(long timestamp, IslOneWayLatency data) {
        IslKey forward = new IslKey(data);

        if (haveValidRoundTripLatencyRecord(forward)) {
            // metric was emitted by round trip latency handler
            return;
        }

        IslKey reverse = forward.getReverse();
        if (haveValidRoundTripLatencyRecord(reverse)) {
            // get RTL metric from reverse ISL and emit it
            emitReverseRoundTripLatency(forward, reverse, timestamp);
            return;
        }

        // there is no RTL records during latency timeout so we have to use one way latency
        oneWayLatencyStorage.putIfAbsent(forward, new TreeMap<>());
        oneWayLatencyStorage.get(forward).put(timestamp, data.getLatency());

        if (!oneWayLatencyEmitTimeoutMap.containsKey(forward)) {
            Instant emitTimeout = Instant.ofEpochMilli(timestamp).plusSeconds(latencyTimeout);
            oneWayLatencyEmitTimeoutMap.put(forward, emitTimeout);
            return;
        }

        Instant emitTimeout = oneWayLatencyEmitTimeoutMap.get(forward);

        if (emitTimeout.isBefore(Instant.ofEpochMilli(timestamp))) {
            // we waited enough to be sure that we wouldn't get a round trip latency packet
            emitValidOneWayRecords(forward);
            clearOneWayRecords(forward);
        } // else still waiting for RTL and collecting one way records
    }

    /**
     * Handle ISL status update notification.
     *
     * @param notification notification with new ISL status
     */
    public void handleIstStatusUpdateNotification(IslStatusUpdateNotification notification) {
        IslKey key = new IslKey(notification.getSrcSwitchId(), notification.getSrcPortNo(),
                notification.getDstSwitchId(), notification.getDstPortNo());

        if (notification.getStatus() == IslStatus.MOVED) {
            handleIslMoved(key);
            handleIslMoved(key.getReverse());
        } else if (notification.getStatus() == IslStatus.INACTIVE) {
            handleIslDown(key);
            handleIslDown(key.getReverse());
        }
    }

    private void handleIslMoved(IslKey key) {
        clearOneWayRecords(key);
        oneWayLatencyEmitTimeoutMap.remove(key);
    }

    private void handleIslDown(IslKey key) {
        if (!(roundTripLatencyStorage.containsKey(key) || roundTripLatencyStorage.containsKey(key.getReverse()))) {
            // there were no RTL records so we have to use one way latency
            emitValidOneWayRecords(key);
        }

        clearOneWayRecords(key);
        oneWayLatencyEmitTimeoutMap.remove(key);
    }

    private void emitValidOneWayRecords(IslKey key) {
        if (!oneWayLatencyStorage.containsKey(key)) {
            return;
        }

        TreeMap<Long, Long> recordMap = oneWayLatencyStorage.get(key);

        long oldestTimestamp = Instant.now().minusSeconds(latencyTimeout).toEpochMilli();

        for (Entry<Long, Long> record : recordMap.tailMap(oldestTimestamp).entrySet()) {
            carrier.emitLatency(
                    key.getSrcSwitchId(),
                    key.getSrcPort(),
                    key.getDstSwitchId(),
                    key.getDstPort(),
                    record.getValue(),
                    record.getKey());
        }
    }

    private void clearOneWayRecords(IslKey key) {
        if (oneWayLatencyStorage.containsKey(key)) {
            oneWayLatencyStorage.get(key).clear();
        }
    }

    private boolean haveValidRoundTripLatencyRecord(IslKey key) {
        if (!roundTripLatencyStorage.containsKey(key)) {
            return false;
        }
        return isRecordStillValid(roundTripLatencyStorage.get(key));
    }

    private void emitReverseRoundTripLatency(IslKey forward, IslKey reverse, long timestamp) {
        // got RTL for reverse ISL
        long reverseRoundTripLatency = roundTripLatencyStorage.get(reverse).getLatency();

        // and emit it for forward ISL
        carrier.emitLatency(
                forward.getSrcSwitchId(),
                forward.getSrcPort(),
                forward.getDstSwitchId(),
                forward.getDstPort(),
                reverseRoundTripLatency,
                timestamp);
    }

    @VisibleForTesting
    boolean isRecordStillValid(LatencyRecord record) {
        Instant expirationTime = Instant.ofEpochMilli(record.getTimestamp())
                .plusSeconds(latencyTimeout);

        return Instant.now().isBefore(expirationTime);
    }
}
