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

package org.openkilda.server42.control.kafka;

import org.openkilda.server42.control.messaging.flowrtt.AddFlow;
import org.openkilda.server42.control.messaging.flowrtt.ClearFlows;
import org.openkilda.server42.control.messaging.flowrtt.Control;
import org.openkilda.server42.control.messaging.flowrtt.Control.CommandPacket;
import org.openkilda.server42.control.messaging.flowrtt.Control.CommandPacket.Builder;
import org.openkilda.server42.control.messaging.flowrtt.Control.CommandPacket.Type;
import org.openkilda.server42.control.messaging.flowrtt.Control.CommandPacketResponse;
import org.openkilda.server42.control.messaging.flowrtt.Control.Flow;
import org.openkilda.server42.control.messaging.flowrtt.Control.Flow.EncapsulationType;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsRequest;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsResponse;
import org.openkilda.server42.control.messaging.flowrtt.PushSettings;
import org.openkilda.server42.control.messaging.flowrtt.RemoveFlow;
import org.openkilda.server42.control.zeromq.ZeroMqClient;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@Slf4j
@KafkaListener(id = "server42-control", topics = "${openkilda.server42.control.kafka.topic.from_storm}")
public class Gate {

    private final KafkaTemplate<String, Object> template;

    private final ZeroMqClient zeroMqClient;

    @Value("${openkilda.server42.control.kafka.topic.to_storm}")
    private String toStorm;

    public Gate(@Autowired KafkaTemplate<String, Object> template,
                @Autowired ZeroMqClient zeroMqClient) {
        this.template = template;
        this.zeroMqClient = zeroMqClient;
    }

    @KafkaHandler
    private void listen(AddFlow data) {
        Builder builder = CommandPacket.newBuilder();
        Flow flow = Flow.newBuilder()
                .setEncapsulationType(EncapsulationType.forNumber(data.getEncapsulationType().ordinal()))
                .setFlowId(data.getFlowId())
                .setTunnelId(data.getTunnelId()).build();
        Control.AddFlow addFlow = Control.AddFlow.newBuilder().setFlow(flow).build();
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
    private void listen(ClearFlows data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.CLEAR_FLOWS);
        try {
            zeroMqClient.send(builder.build());
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }

    @KafkaHandler
    private void listen(ListFlowsRequest data) {
        Builder builder = CommandPacket.newBuilder();
        builder.setType(Type.LIST_FLOWS);
        try {
            CommandPacketResponse serverResponse = zeroMqClient.send(builder.build());
            HashSet<String> flowList = new HashSet<>();
            for (Any any: serverResponse.getResponseList()) {
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
    private void listen(PushSettings data) {
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
    private void listen(RemoveFlow data) {
        Builder builder = CommandPacket.newBuilder();
        Flow flow = Flow.newBuilder().setFlowId(data.getFlowId()).build();
        Control.RemoveFlow removeFlow = Control.RemoveFlow.newBuilder().setFlow(flow).build();
        builder.setType(Type.REMOVE_FLOW);
        builder.addCommand(Any.pack(removeFlow));
        CommandPacket packet = builder.build();
        try {
            zeroMqClient.send(packet);
        } catch (InvalidProtocolBufferException e) {
            log.error("Marshalling error on {}", data);
        }
    }
}
