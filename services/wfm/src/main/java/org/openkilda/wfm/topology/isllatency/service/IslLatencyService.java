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
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.isllatency.model.IslKey;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

@Slf4j
public class IslLatencyService {
    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private SwitchRepository switchRepository;
    private final long latencyUpdateIntervalInMilliseconds; // emit data in DB interval
    private final long latencyUpdateTimeRangeInMilliseconds; // average latency will be calculated in this time range

    private Map<IslKey, Queue<LatencyRecord>> roundTripLatencyStorage;
    private Map<IslKey, Queue<LatencyRecord>> oneWayLatencyStorage;
    private Map<IslKey, Boolean> roundTripLatencyIsSet; // True if DB stored round trip latency, False if one way
    private Map<IslKey, Long> nextUpdateTimeMap;

    public IslLatencyService(TransactionManager transactionManager,
                             RepositoryFactory repositoryFactory, long latencyUpdateIntervalInMilliseconds,
                             long latencyUpdateTimeRangeInMilliseconds) {
        this.transactionManager = transactionManager;
        this.latencyUpdateIntervalInMilliseconds = latencyUpdateIntervalInMilliseconds;
        this.latencyUpdateTimeRangeInMilliseconds = latencyUpdateTimeRangeInMilliseconds;
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        oneWayLatencyStorage = new HashMap<>();
        roundTripLatencyStorage = new HashMap<>();
        roundTripLatencyIsSet = new HashMap<>();
        nextUpdateTimeMap = new HashMap<>();
    }

    /**
     * Handle round trip latency.
     *
     * @param data round trip latency info data
     * @param destination isl destination endpoint
     * @param timestamp latency timestamp
     */
    public void handleRoundTripIslLatency(IslRoundTripLatency data, Endpoint destination, long timestamp) {
        if (data.getLatency() < 0) {
            log.info("Received invalid round trip latency {} for ISL {}_{} ===> {}_{}. Packet Id: {}",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    destination.getDatapath(), destination.getPortNumber(), data.getPacketId());
            return;
        }

        IslKey islKey = new IslKey(data, destination);

        roundTripLatencyStorage.putIfAbsent(islKey, new LinkedList<>());
        roundTripLatencyStorage.get(islKey).add(new LatencyRecord(data.getLatency(), timestamp));

        if (System.currentTimeMillis() >= nextUpdateTimeMap.getOrDefault(islKey, 0L)
                || !roundTripLatencyIsSet.getOrDefault(islKey, false)) {
            updateRoundTripLatency(data, destination, islKey);
        }
    }

    /**
     * Handle one way latency metric.
     *
     * @param data isl one way latency info data
     * @param timestamp latency timestamp
     */
    public void handleOneWayIslLatency(IslOneWayLatency data, long timestamp) {
        if (data.getLatency() < 0) {
            log.info("Received invalid one way latency {} for ISL {}_{} ===> {}_{}. Packet Id: {}",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
            return;
        }


        IslKey islKey = new IslKey(data);

        oneWayLatencyStorage.putIfAbsent(islKey, new LinkedList<>());
        oneWayLatencyStorage.get(islKey).add(new LatencyRecord(data.getLatency(), timestamp));

        if (System.currentTimeMillis() >= nextUpdateTimeMap.getOrDefault(islKey, 0L)) {
            updateOneWayLatencyIfNeeded(data, islKey);
        }
    }

    private void updateRoundTripLatency(IslRoundTripLatency data, Endpoint destination, IslKey islKey) {
        Queue<LatencyRecord> roundTripRecords = roundTripLatencyStorage.get(islKey);

        pollExpiredRecords(roundTripRecords);
        pollExpiredRecords(oneWayLatencyStorage.get(islKey));

        long averageLatency = calculateAverageLatency(roundTripRecords);

        updateLatencyInDataBase(data, destination, averageLatency);
        nextUpdateTimeMap.put(islKey, System.currentTimeMillis() + latencyUpdateIntervalInMilliseconds);
        roundTripLatencyIsSet.put(islKey, true);
    }

    private void updateOneWayLatencyIfNeeded(IslOneWayLatency data, IslKey islKey) {
        Queue<LatencyRecord> oneWayRecords = oneWayLatencyStorage.get(islKey);
        pollExpiredRecords(oneWayRecords);

        Queue<LatencyRecord> roundTripRecords = roundTripLatencyStorage.get(islKey);
        pollExpiredRecords(roundTripRecords);
        if (roundTripRecords != null && !roundTripRecords.isEmpty()) {
            // next round trip latency packet will update ISL latency
            return;
        }

        IslKey reverseIslKey = islKey.getReverse();
        Queue<LatencyRecord> reverseRoundTripRecords = roundTripLatencyStorage.get(reverseIslKey);
        pollExpiredRecords(reverseRoundTripRecords);

        if (reverseRoundTripRecords != null && !reverseRoundTripRecords.isEmpty()) {
            // reverse ISL has round trip latency records. We can use them for forward ISL
            long averageReverseLatency = calculateAverageLatency(reverseRoundTripRecords);
            updateLatencyInDataBase(data, averageReverseLatency);
        } else {
            // There are no round trip latency records for both ISL direction. We have to use one way latency records
            long averageOneWayLatency = calculateAverageLatency(oneWayRecords);
            updateLatencyInDataBase(data, averageOneWayLatency);
        }

        nextUpdateTimeMap.put(islKey, System.currentTimeMillis() + latencyUpdateIntervalInMilliseconds);
        roundTripLatencyIsSet.put(islKey, false);
    }

    private void updateLatencyInDataBase(IslOneWayLatency data, long latency) {
        updateLatencyInDataBase(data.getSrcSwitchId(), data.getSrcPortNo(), data.getDstSwitchId(), data.getDstPortNo(),
                latency, data.getPacketId(), "one way");
    }

    private void updateLatencyInDataBase(IslRoundTripLatency data, Endpoint destination, long latency) {
        updateLatencyInDataBase(data.getSrcSwitchId(), data.getSrcPortNo(),
                destination.getDatapath(), destination.getPortNumber(), latency, data.getPacketId(), "round trip");
    }

    private void updateLatencyInDataBase(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort,
                                         long latency, long packetId, String latencyType) {
        try {
            updateIslLatency(srcSwitch, srcPort, dstSwitch, dstPort, latency);
            log.info("Updated {} latency for ISL {}_{} ===( {} ms )===> {}_{}. Packet id:{}",
                    latencyType, srcSwitch, srcPort, latency, dstSwitch, dstPort, packetId);
        } catch (SwitchNotFoundException | IslNotFoundException e) {
            log.warn("Couldn't update {} latency for ISL {}_{} ===> {}_{}. Packet id:{}. {}",
                    latencyType, srcSwitch, srcPort, dstSwitch, dstPort, packetId, e.getMessage());
        }
    }

    private void pollExpiredRecords(Queue<LatencyRecord> recordsQueue) {
        if (recordsQueue == null) {
            return;
        }
        long expirationTime = System.currentTimeMillis() - latencyUpdateTimeRangeInMilliseconds;
        while (!recordsQueue.isEmpty() && recordsQueue.peek().getTimestamp() < expirationTime) {
            recordsQueue.poll();
        }
    }

    private long calculateAverageLatency(Queue<LatencyRecord> recordsQueue) {
        long sum = 0;
        for (LatencyRecord record : recordsQueue) {
            sum += record.getLatency();
        }
        return sum / recordsQueue.size();
    }

    /**
     * Update latency of isl by source and destination endpoint.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param dstSwitchId ID of destination switch.
     * @param dstPort destination port.
     * @param latency latency to update
     *
     * @throws SwitchNotFoundException if src or dst switch is not found
     * @throws IslNotFoundException if isl is not found
     */
    @VisibleForTesting
    void updateIslLatency(
            SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort, long latency)
            throws SwitchNotFoundException, IslNotFoundException {
        transactionManager.doInTransaction(() -> {
            Switch srcSwitch = switchRepository.findById(srcSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(srcSwitchId));
            Switch dstSwitch = switchRepository.findById(dstSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(dstSwitchId));

            switchRepository.lockSwitches(srcSwitch, dstSwitch);
            Isl isl = islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                    .orElseThrow(() -> new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort));
            isl.setLatency(latency);
            islRepository.createOrUpdate(isl);
        });
    }
}
