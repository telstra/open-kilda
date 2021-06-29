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

package org.openkilda.server42.control.serverstub;

import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl.Flow;
import org.openkilda.server42.stats.messaging.Statistics.FlowLatencyPacket;
import org.openkilda.server42.stats.messaging.Statistics.IslLatencyPacket;
import org.openkilda.server42.stats.messaging.Statistics.LatencyPacketBucket;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

/**
 * Send statistics to stats application.
 */
@Service
@Slf4j
public class StatsServer extends Thread {

    @Value
    @Builder
    static class FlowStats {
        String flowId;
        Boolean direction;
        Long baseLatency;
    }

    @Value
    @Builder
    static class IslKey {
        String switchId;
        int port;
    }

    private static final int MIN_BASE_LATENCY = 100;
    private static final int MAX_BASE_LATENCY = 10000;

    private static final int MIN_DELTA_LATENCY = 10;
    private static final int MAX_DELTA_LATENCY = 100;

    private Map<FlowKey, FlowStats> flows = new HashMap<>();
    private Map<IslKey, Long> isls = new HashMap<>();
    private long tickSize = 500;

    @org.springframework.beans.factory.annotation.Value("${openkilda.server42.control.zeromq.stats.server.endpoint}")
    private String bindEndpoint;

    /**
     * Generate packet statistics.
     */
    @Override
    public void run() {
        log.info("started");
        try (ZContext context = new ZContext()) {
            Socket server = context.createSocket(ZMQ.PUSH);
            server.bind(bindEndpoint);
            while (!isInterrupted()) {
                LatencyPacketBucket.Builder bucketBuilder = LatencyPacketBucket.newBuilder();
                long seconds = TimeUnit.MILLISECONDS.toSeconds(Instant.now().toEpochMilli());
                long timestamp = seconds << 32;
                synchronized (this) {
                    FlowLatencyPacket.Builder flowLatencyPacketBuilder = FlowLatencyPacket.newBuilder();
                    for (FlowStats flow : flows.values()) {
                        flowLatencyPacketBuilder.setFlowId(flow.flowId);
                        flowLatencyPacketBuilder.setDirection(flow.direction);
                        flowLatencyPacketBuilder.setT0(timestamp);

                        long generatedLatency = MIN_DELTA_LATENCY
                                + (long) (Math.random() * (MAX_DELTA_LATENCY - MIN_DELTA_LATENCY));
                        flowLatencyPacketBuilder.setT1(timestamp + flow.baseLatency + generatedLatency);
                        bucketBuilder.addFlowLatencyPacket(flowLatencyPacketBuilder.build());
                    }

                    IslLatencyPacket.Builder islLatencyPacketBuilder = IslLatencyPacket.newBuilder();
                    for (Map.Entry<IslKey, Long> isl : isls.entrySet()) {
                        islLatencyPacketBuilder.setSwitchId(isl.getKey().getSwitchId());
                        islLatencyPacketBuilder.setPort(isl.getKey().getPort());
                        islLatencyPacketBuilder.setT0(timestamp);

                        long generatedLatency = MIN_DELTA_LATENCY
                                + (long) (Math.random() * (MAX_DELTA_LATENCY - MIN_DELTA_LATENCY));
                        islLatencyPacketBuilder.setT1(timestamp + isl.getValue() + generatedLatency);
                        bucketBuilder.addIslLatencyPacket(islLatencyPacketBuilder.build());
                    }
                }

                server.send(bucketBuilder.build().toByteArray());
                if (flows.isEmpty() && isls.isEmpty()) {
                    log.info("send ping");
                } else {
                    log.info("send stats");
                }

                try {
                    sleep(tickSize);
                } catch (InterruptedException e) {
                    log.info("received shutdown, exiting from server loop");
                    return;
                }
            }
        }
    }

    @PostConstruct
    void init() {
        this.start();
    }

    /**
     * Add flow to packet generator.
     */
    public synchronized void addFlow(Flow flow) {

        long generatedLatency = MIN_BASE_LATENCY + (long) (Math.random() * (MAX_BASE_LATENCY - MIN_BASE_LATENCY));

        FlowStats flowStats = FlowStats.builder()
                .flowId(flow.getFlowId())
                .direction(flow.getDirection())
                .baseLatency(generatedLatency)
                .build();

        flows.put(FlowKey.fromFlow(flow), flowStats);
    }

    public synchronized void removeFlow(FlowKey flowKey) {
        flows.remove(flowKey);
    }

    public synchronized void clearFlows() {
        flows.clear();
    }

    /**
     * Add ISL to packet generator.
     */
    public synchronized void addIsl(String switchId, int port) {
        long generatedLatency = MIN_BASE_LATENCY + (long) (Math.random() * (MAX_BASE_LATENCY - MIN_BASE_LATENCY));
        isls.put(IslKey.builder().switchId(switchId).port(port).build(), generatedLatency);
    }

    public synchronized void removeIsl(String switchId, int port) {
        isls.remove(IslKey.builder().switchId(switchId).port(port).build());
    }

    public synchronized void clearIsls() {
        isls.clear();
    }
}
