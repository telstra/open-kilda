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

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.model.CacheEndpoint;
import org.openkilda.wfm.topology.isllatency.model.IslKey;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;
import org.openkilda.wfm.topology.isllatency.service.IslLatencyService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

@Slf4j
public class IslLatencyBolt extends AbstractBolt {
    private final PersistenceManager persistenceManager;
    private final long latencyUpdateIntervalInMilliseconds; // emit data in DB interval
    private final long latencyUpdateTimeRangeInMilliseconds; // average latency will be calculated in this time range
    private transient IslLatencyService islLatencyService;
    private Map<IslKey, Queue<LatencyRecord>> roundTripLatencyStorage;
    private Map<IslKey, Queue<LatencyRecord>> oneWayLatencyStorage;

    private Map<IslKey, Boolean> roundTripLatencyIsSet; // True if DB stored round trip latency, False if one way

    private Map<IslKey, Long> nextUpdateTimeMap;

    public IslLatencyBolt(PersistenceManager persistenceManager, long latencyUpdateIntervalInMilliseconds,
                          long latencyUpdateTimeRangeInMilliseconds) {
        this.persistenceManager = persistenceManager;
        this.latencyUpdateIntervalInMilliseconds = latencyUpdateIntervalInMilliseconds;
        this.latencyUpdateTimeRangeInMilliseconds = latencyUpdateTimeRangeInMilliseconds;
    }

    @Override
    protected void init() {
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        islLatencyService = new IslLatencyService(transactionManager, repositoryFactory);
        roundTripLatencyStorage = new HashMap<>();
        oneWayLatencyStorage = new HashMap<>();
        nextUpdateTimeMap = new HashMap<>();
        roundTripLatencyIsSet = new HashMap<>();
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        InfoData data = pullValue(input, LATENCY_DATA_FIELD, InfoData.class);
        long timestamp = input.getLongByField(TIMESTAMP_FIELD);

        if (data instanceof IslRoundTripLatency) {
            handleRoundTripIslLatency(input, (IslRoundTripLatency) data, timestamp);
        } else if (data instanceof IslOneWayLatency) {
            handleOneWayIslLatency((IslOneWayLatency) data, timestamp);
        } else {
            unhandledInput(input);
        }
    }

    private void handleRoundTripIslLatency(Tuple input, IslRoundTripLatency data, long timestamp)
            throws PipelineException {
        CacheEndpoint destination = pullValue(input, CACHE_DATA_FIELD, CacheEndpoint.class);

        if (data.getLatency() < 0) {
            log.info("Received invalid round trip latency {} for ISL {}_{} ===> {}_{}. Packet Id: {}",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    destination.getSwitchId(), destination.getPort(), data.getPacketId());
            return;
        }

        IslKey islKey = new IslKey(data, destination);

        roundTripLatencyStorage.putIfAbsent(islKey, new LinkedList<>());
        roundTripLatencyStorage.get(islKey).add(new LatencyRecord(data.getLatency(), timestamp));

        if (!nextUpdateTimeMap.containsKey(islKey) || System.currentTimeMillis() >= nextUpdateTimeMap.get(islKey)
                || !roundTripLatencyIsSet.getOrDefault(islKey, false)) {
            updateRoundTripLatency(data, destination, islKey);
        }
    }

    private void updateRoundTripLatency(IslRoundTripLatency data, CacheEndpoint destination, IslKey islKey) {
        Queue<LatencyRecord> roundTripRecords = roundTripLatencyStorage.get(islKey);

        pollExpiredRecords(roundTripRecords);
        pollExpiredRecords(oneWayLatencyStorage.get(islKey));

        long averageLatency = calculateAverageLatency(roundTripRecords);

        updateLatencyInDataBase(data, destination, averageLatency);
        nextUpdateTimeMap.put(islKey, System.currentTimeMillis() + latencyUpdateIntervalInMilliseconds);
        roundTripLatencyIsSet.put(islKey, true);
    }

    private void handleOneWayIslLatency(IslOneWayLatency data, long timestamp) {
        if (data.getLatency() < 0) {
            log.info("Received invalid one way latency {} for ISL {}_{} ===> {}_{}. Packet Id: {}",
                    data.getLatency(), data.getSrcSwitchId(), data.getSrcPortNo(),
                    data.getDstSwitchId(), data.getDstPortNo(), data.getPacketId());
            return;
        }


        IslKey islKey = new IslKey(data);

        oneWayLatencyStorage.putIfAbsent(islKey, new LinkedList<>());
        oneWayLatencyStorage.get(islKey).add(new LatencyRecord(data.getLatency(), timestamp));

        if (!nextUpdateTimeMap.containsKey(islKey) || System.currentTimeMillis() >= nextUpdateTimeMap.get(islKey)) {
            updateOneWayLatencyIfNeeded(data, islKey);
        }
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

    private void updateLatencyInDataBase(IslRoundTripLatency data, CacheEndpoint destination, long latency) {
        updateLatencyInDataBase(data.getSrcSwitchId(), data.getSrcPortNo(),
                destination.getSwitchId(), destination.getPort(), latency, data.getPacketId(), "round trip");
    }

    private void updateLatencyInDataBase(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort,
                                         long latency, long packetId, String latencyType) {
        try {
            islLatencyService.updateIslLatency(srcSwitch, srcPort, dstSwitch, dstPort, latency);
        } catch (SwitchNotFoundException | IslNotFoundException e) {
            log.warn("Couldn't update {} latency for ISL {}_{} ===> {}_{}. Packet id:{}. {}",
                    latencyType, srcSwitch, srcPort, dstSwitch, dstPort, packetId, e.getMessage());
        }
        log.info("Updated {} latency for ISL {}_{} ===( {} ms )===> {}_{}. Packet id:{}",
                latencyType, srcSwitch, srcPort, latency, dstSwitch, dstPort, packetId);
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

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
