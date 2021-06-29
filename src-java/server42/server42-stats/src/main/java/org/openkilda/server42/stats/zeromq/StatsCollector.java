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

package org.openkilda.server42.stats.zeromq;

import static java.lang.String.format;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.info.stats.IslRttStatsData;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.server42.stats.messaging.Statistics.FlowLatencyPacket;
import org.openkilda.server42.stats.messaging.Statistics.IslLatencyPacket;
import org.openkilda.server42.stats.messaging.Statistics.LatencyPacketBucket;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
@Slf4j
public class StatsCollector extends Thread {
    private final KafkaTemplate<String, Object> template;

    @Value("${openkilda.server42.stats.zeromq.server.endpoint}")
    private String connectEndpoint;

    @Value("${openkilda.server42.stats.kafka.topic.flowrtt.to_storm}")
    private String flowStatToStormTopic;

    @Value("${openkilda.server42.stats.kafka.topic.islrtt.to_storm}")
    private String islStatToStormTopic;

    private String sessionId;
    private ZContext context;

    public StatsCollector(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    @PostConstruct
    private void init() {
        context = new ZContext();
        this.start();
    }

    @PreDestroy
    private void clear() {
        context.close();
    }

    /**
     * Connect to server42 and get statistics.
     */
    @Override
    public void run() {
        sessionId = RandomStringUtils.randomAlphanumeric(8);
        log.info("started with session id {}", sessionId);
        while (!isInterrupted()) {

            Socket server = null;
            try {
                server = context.createSocket(ZMQ.PULL);
                server.setReceiveTimeOut(1000);
                server.connect(connectEndpoint);
                log.info("connect to {}", connectEndpoint);
                while (!isInterrupted()) {
                    byte[] recv = server.recv();
                    log.debug("recv {}", recv);
                    if (recv == null && server.errno() == ZError.EAGAIN) {
                        log.debug("EAGAIN received");
                        break;
                    } else if (recv != null && recv.length == 0) {
                        log.debug("ping received");
                        continue;
                    }
                    log.debug("stats received");
                    handleInput(recv);
                }
            } catch (org.zeromq.ZMQException ex) {
                log.error(ex.toString());
            } finally {
                log.info("disconnected");
                if (server != null) {
                    context.destroySocket(server);
                }
            }
        }
    }


    private void handleInput(byte[] recv) {
        try {
            LatencyPacketBucket latencyPacketBucket = LatencyPacketBucket.parseFrom(recv);
            log.debug("getFlowLatencyPacketList size: {}, getIslLatencyPacketList size: {}",
                    latencyPacketBucket.getFlowLatencyPacketList().size(),
                    latencyPacketBucket.getIslLatencyPacketList().size());

            sendStats(latencyPacketBucket);

        } catch (InvalidProtocolBufferException e) {
            log.error(e.toString());
        }
    }

    void sendStats(LatencyPacketBucket latencyPacketBucket) throws InvalidProtocolBufferException {

        long currentTimeMillis = System.currentTimeMillis();
        for (FlowLatencyPacket packet : latencyPacketBucket.getFlowLatencyPacketList()) {
            FlowRttStatsData data = new FlowRttStatsData(
                    packet.getFlowId(),
                    FlowDirection.fromBoolean(packet.getDirection()).name().toLowerCase(),
                    packet.getT0(),
                    packet.getT1()
            );

            InfoMessage message = new InfoMessage(data, currentTimeMillis,
                    format("stats42-%s-%d", sessionId, packet.getPacketId()));
            log.debug("InfoMessage {}", message);
            template.send(flowStatToStormTopic, packet.getFlowId(), message);
        }

        for (IslLatencyPacket packet : latencyPacketBucket.getIslLatencyPacketList()) {
            IslRttStatsData data = new IslRttStatsData(
                    packet.getSwitchId(),
                    packet.getPort(),
                    packet.getT0(),
                    packet.getT1(),
                    "server42"
            );

            String key = format("%s-%d", packet.getSwitchId(), packet.getPort());
            InfoMessage message = new InfoMessage(data, currentTimeMillis,
                    format("stats42-%s-isl-%s", sessionId, key));
            log.debug("InfoMessage {}", message);
            template.send(islStatToStormTopic, key, message);
        }
    }
}
