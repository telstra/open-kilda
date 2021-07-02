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

package org.openkilda.server42.control.kafka;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.config.SwitchToVlanMapping;
import org.openkilda.server42.control.messaging.Control;
import org.openkilda.server42.control.messaging.Control.CommandPacket;
import org.openkilda.server42.control.messaging.Control.CommandPacket.Builder;
import org.openkilda.server42.control.messaging.Control.CommandPacket.Type;
import org.openkilda.server42.control.messaging.Control.CommandPacketResponse;
import org.openkilda.server42.control.messaging.flowrtt.AddFlow;
import org.openkilda.server42.control.messaging.flowrtt.ClearFlows;
import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl;
import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl.Flow;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsOnSwitch;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsRequest;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsResponse;
import org.openkilda.server42.control.messaging.flowrtt.PushSettings;
import org.openkilda.server42.control.messaging.flowrtt.RemoveFlow;
import org.openkilda.server42.control.messaging.islrtt.AddIsl;
import org.openkilda.server42.control.messaging.islrtt.ClearIsls;
import org.openkilda.server42.control.messaging.islrtt.IslRttControl;
import org.openkilda.server42.control.messaging.islrtt.IslRttControl.IslEndpoint;
import org.openkilda.server42.control.messaging.islrtt.ListIslPortsOnSwitch;
import org.openkilda.server42.control.messaging.islrtt.ListIslsRequest;
import org.openkilda.server42.control.messaging.islrtt.ListIslsResponse;
import org.openkilda.server42.control.messaging.islrtt.RemoveIsl;
import org.openkilda.server42.control.zeromq.ZeroMqClient;
import org.openkilda.server42.messaging.FlowDirection;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main class for that server. It listen messages from storm by kafka filtered by KafkaRecordFilter than repack it
 * to protobuf and retranslate it to Server42cpp application by ZeroMq.
 */
@Service
@Slf4j
@KafkaListener(id = "server42-control",
        topics = "${openkilda.server42.control.kafka.topic.from_storm}",
        idIsGroup = false
)
public class Gate implements ConsumerSeekAware {

    private final KafkaTemplate<String, Object> template;

    private final ZeroMqClient zeroMqClient;

    @Value("${openkilda.server42.control.kafka.topic.to_storm}")
    private String toStorm;

    @Value("${openkilda.server42.control.flow_rtt.udp_src_port_offset}")
    private Integer flowRttUdpSrcPortOffset;

    @Value("${openkilda.server42.control.isl_rtt.udp_src_port_offset}")
    private Integer islRttUdpSrcPortOffset;

    private Map<String, Long> switchToVlanMap;

    public Gate(@Autowired KafkaTemplate<String, Object> template,
                @Autowired ZeroMqClient zeroMqClient,
                @Autowired SwitchToVlanMapping switchToVlanMapping) {
        this.template = template;
        this.zeroMqClient = zeroMqClient;
        this.switchToVlanMap = switchToVlanMapping.getVlan().entrySet().stream().flatMap(
                vlanToSwitches -> vlanToSwitches.getValue().stream().map(
                        switchId -> new SimpleEntry<>(switchId, vlanToSwitches.getKey()))
        ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        log.info("seek to end");
        assignments.forEach((t, o) -> callback.seekToEnd(t.topic(), t.partition()));
    }

    @KafkaHandler
    void listen(@Payload AddFlow data,
                @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String switchIdKey) {

        SwitchId switchId = new SwitchId(switchIdKey);

        Builder builder = CommandPacket.newBuilder();
        Flow flow = Flow.newBuilder()
                .setFlowId(data.getFlowId())
                .setEncapsulationType(Flow.EncapsulationType.VLAN)
                .setTunnelId(data.getTunnelId())
                .setTransitEncapsulationType(Flow.EncapsulationType.VLAN)
                .setInnerTunnelId(data.getInnerTunnelId())
                .setTransitTunnelId(switchToVlanMap.get(switchIdKey))
                .setDirection(FlowDirection.toBoolean(data.getDirection()))
                .setUdpSrcPort(flowRttUdpSrcPortOffset + data.getPort())
                .setDstMac(switchId.toMacAddress())
                .setHashCode(data.hashCode())
                .build();

        FlowRttControl.AddFlow addFlow = FlowRttControl.AddFlow.newBuilder().setFlow(flow).build();
        builder.setType(Type.ADD_FLOW);
        builder.addCommand(Any.pack(addFlow));
        CommandPacket packet = builder.build();
        try {
            zeroMqClient.send(packet);
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(ClearFlows data,
                @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String switchIdKey) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.CLEAR_FLOWS);
        SwitchId switchId = new SwitchId(switchIdKey);
        FlowRttControl.ClearFlowsFilter clearFlowsFilter = FlowRttControl.ClearFlowsFilter.newBuilder()
                .setDstMac(switchId.toMacAddress()).build();
        builder.addCommand(Any.pack(clearFlowsFilter));
        try {
            zeroMqClient.send(builder.build());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(ListFlowsRequest data,
                @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String switchIdKey) {
        CommandPacket commandPacket = getFlowListCommandPacket(switchIdKey);
        try {
            CommandPacketResponse serverResponse = zeroMqClient.send(commandPacket);

            if (serverResponse == null) {
                log.error("No response from server on {}", data.getHeaders().getCorrelationId());
                return;
            }

            HashSet<String> flowList = new HashSet<>();
            for (Any any : serverResponse.getResponseList()) {
                flowList.add(any.unpack(Flow.class).getFlowId());
            }

            ListFlowsResponse response = ListFlowsResponse.builder()
                    .headers(data.getHeaders())
                    .flowIds(flowList).build();

            template.send(toStorm, response);
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(ListFlowsOnSwitch data,
                @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String switchIdKey) {

        CommandPacket commandPacket = getFlowListCommandPacket(switchIdKey);

        try {
            CommandPacketResponse serverResponse = zeroMqClient.send(commandPacket);
            if (serverResponse == null) {
                log.error("No response from server on {}", data.getHeaders().getCorrelationId());
                return;
            }

            for (Any any : serverResponse.getResponseList()) {
                String flowId = any.unpack(Flow.class).getFlowId();
                if (!data.getFlowIds().contains(flowId)) {
                    removeFlow(flowId, FlowDirection.FORWARD);
                    removeFlow(flowId, FlowDirection.REVERSE);
                }
            }

        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(PushSettings data) {
        Builder builder = CommandPacket.newBuilder();
        Control.PushSettings pushSettings = Control.PushSettings.newBuilder()
                .setPacketGenerationIntervalInMs(data.getPacketGenerationIntervalInMs()).build();
        builder.setType(Type.PUSH_SETTINGS);
        builder.addCommand(Any.pack(pushSettings));
        CommandPacket packet = builder.build();
        try {
            zeroMqClient.send(packet);
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(RemoveFlow data) {
        try {
            removeFlow(data.getFlowId(), data.getDirection());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    void listen(@Payload AddIsl data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.ADD_ISL);
        SwitchId switchId = data.getSwitchId();
        String switchIdKey = switchId.toString();
        IslEndpoint endpoint = IslEndpoint.newBuilder()
                .setSwitchId(switchIdKey).setPort(data.getPort()).build();

        IslRttControl.AddIsl addIsl = IslRttControl.AddIsl.newBuilder()
                .setIsl(endpoint).setSwitchMac(switchId.toMacAddress())
                .setUdpSrcPort(islRttUdpSrcPortOffset + data.getPort())
                .setTransitEncapsulationType(IslRttControl.AddIsl.EncapsulationType.VLAN)
                .setTransitTunnelId(switchToVlanMap.get(switchIdKey))
                .setHashCode(data.hashCode())
                .build();
        builder.addCommand(Any.pack(addIsl));
        try {
            zeroMqClient.send(builder.build());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data, e);
        }
    }

    @KafkaHandler
    void listen(@Payload ClearIsls data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.CLEAR_ISLS);
        IslRttControl.ClearIslsFilter clearIslsFilter = IslRttControl.ClearIslsFilter.newBuilder()
                .setSwitchId(data.getSwitchId().toString())
                .build();
        builder.addCommand(Any.pack(clearIslsFilter));
        try {
            zeroMqClient.send(builder.build());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data, e);
        }
    }

    @KafkaHandler
    void listen(@Payload ListIslsRequest data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.LIST_ISLS);
        IslRttControl.ListIslsFilter listIslsFilter = IslRttControl.ListIslsFilter.newBuilder()
                .setSwitchId(data.getSwitchId().toString()).build();
        builder.addCommand(Any.pack(listIslsFilter));
        try {
            CommandPacketResponse serverResponse = zeroMqClient.send(builder.build());
            if (serverResponse == null) {
                log.error("No response from server on {}", data.getHeaders().getCorrelationId());
                return;
            }

            HashSet<Integer> portList = new HashSet<>();
            for (Any any : serverResponse.getResponseList()) {
                portList.add(any.unpack(IslEndpoint.class).getPort());
            }

            ListIslsResponse response = ListIslsResponse.builder()
                    .headers(data.getHeaders())
                    .switchId(data.getSwitchId())
                    .ports(portList).build();

            template.send(toStorm, response);
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data, e);
        }
    }

    @KafkaHandler
    void listen(@Payload ListIslPortsOnSwitch data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.LIST_ISLS);
        IslRttControl.ListIslsFilter listIslsFilter = IslRttControl.ListIslsFilter.newBuilder()
                .setSwitchId(data.getSwitchId().toString()).build();
        builder.addCommand(Any.pack(listIslsFilter));
        try {
            CommandPacketResponse serverResponse = zeroMqClient.send(builder.build());
            if (serverResponse == null) {
                log.error("No response from server on {}", data.getHeaders().getCorrelationId());
                return;
            }

            for (Any any : serverResponse.getResponseList()) {
                IslRttControl.IslEndpoint endpoint = any.unpack(IslRttControl.IslEndpoint.class);
                if (!data.getIslPorts().contains(endpoint.getPort())) {
                    removeIsl(data.getSwitchId(), endpoint.getPort());
                }
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data, e);
        }
    }

    @KafkaHandler
    void listen(@Payload RemoveIsl data) {
        removeIsl(data.getSwitchId(), data.getPort());
    }

    private void removeFlow(String flowId, FlowDirection direction) throws InvalidProtocolBufferException {
        Builder builder = CommandPacket.newBuilder();
        Flow flow = Flow.newBuilder()
                .setFlowId(flowId)
                .setDirection(FlowDirection.toBoolean(direction))
                .build();
        FlowRttControl.RemoveFlow removeFlow = FlowRttControl.RemoveFlow.newBuilder().setFlow(flow).build();
        builder.setType(Type.REMOVE_FLOW);
        builder.addCommand(Any.pack(removeFlow));
        CommandPacket packet = builder.build();
        zeroMqClient.send(packet);
    }

    private CommandPacket getFlowListCommandPacket(String switchIdKey) {
        SwitchId switchId = new SwitchId(switchIdKey);
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.LIST_FLOWS);

        FlowRttControl.ListFlowsFilter listFlowsFilter = FlowRttControl.ListFlowsFilter.newBuilder()
                .setDstMac(switchId.toMacAddress()).build();
        builder.addCommand(Any.pack(listFlowsFilter));
        return builder.build();
    }

    private void removeIsl(SwitchId switchId, int port) {
        Builder builder = CommandPacket.newBuilder();
        IslEndpoint endpoint = IslEndpoint.newBuilder()
                .setSwitchId(switchId.toString()).setPort(port).build();
        IslRttControl.RemoveIsl removeIsl = IslRttControl.RemoveIsl.newBuilder()
                .setIsl(endpoint).build();
        builder.setType(Type.REMOVE_ISL);
        builder.addCommand(Any.pack(removeIsl));
        try {
            zeroMqClient.send(builder.build());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {} / {}", switchId, port, e);
        }
    }
}
