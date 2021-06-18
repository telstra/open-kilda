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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.config.SwitchToVlanMapping;
import org.openkilda.server42.control.messaging.Control;
import org.openkilda.server42.control.messaging.Control.CommandPacket;
import org.openkilda.server42.control.messaging.Control.CommandPacket.Type;
import org.openkilda.server42.control.messaging.Control.CommandPacketResponse;
import org.openkilda.server42.control.messaging.Control.CommandPacketResponse.Builder;
import org.openkilda.server42.control.messaging.flowrtt.AddFlow;
import org.openkilda.server42.control.messaging.flowrtt.ClearFlows;
import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl;
import org.openkilda.server42.control.messaging.flowrtt.FlowRttControl.Flow;
import org.openkilda.server42.control.messaging.flowrtt.Headers;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsRequest;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsResponse;
import org.openkilda.server42.control.messaging.flowrtt.PushSettings;
import org.openkilda.server42.control.messaging.flowrtt.RemoveFlow;
import org.openkilda.server42.control.messaging.islrtt.AddIsl;
import org.openkilda.server42.control.messaging.islrtt.ClearIsls;
import org.openkilda.server42.control.messaging.islrtt.IslRttControl;
import org.openkilda.server42.control.messaging.islrtt.IslRttControl.IslEndpoint;
import org.openkilda.server42.control.messaging.islrtt.ListIslsRequest;
import org.openkilda.server42.control.messaging.islrtt.ListIslsResponse;
import org.openkilda.server42.control.messaging.islrtt.RemoveIsl;
import org.openkilda.server42.control.zeromq.ZeroMqClient;
import org.openkilda.server42.messaging.FlowDirection;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Gate.class})
@ContextConfiguration(classes = SwitchToVlanMapping.class)
@TestPropertySource("classpath:test.properties")
@MockBean(value = {
        ZeroMqClient.class,
        KafkaTemplate.class
})
public class GateTest {

    @MockBean
    KafkaTemplate<String, Object> template;

    @MockBean
    ZeroMqClient zeroMqClient;

    @Autowired
    private Gate gate;

    @Value("${openkilda.server42.control.flow_rtt.udp_src_port_offset}")
    private Integer flowRttUdpSrcPortOffset;

    @Value("${openkilda.server42.control.isl_rtt.udp_src_port_offset}")
    private Integer islRttUdpSrcPortOffset;

    @Value("${openkilda.server42.control.kafka.topic.to_storm}")
    private String toStorm;


    @Autowired
    private SwitchToVlanMapping switchToVlanMapping;

    @Test
    public void addFlow() throws Exception {

        AddFlow addFlow = AddFlow.builder()
                .flowId("some-flow-id")
                .tunnelId(1001L)
                .innerTunnelId(1002L)
                .direction(FlowDirection.REVERSE)
                .port(42)
                .build();

        String switchId = "00:00:1b:45:18:d6:71:5a";

        gate.listen(addFlow, switchId);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.ADD_FLOW);

        assertThat(commandPacket.getCommandCount()).isEqualTo(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(FlowRttControl.AddFlow.class)).isTrue();

        FlowRttControl.AddFlow unpack = command.unpack(FlowRttControl.AddFlow.class);

        Flow flow = unpack.getFlow();

        assertThat(flow.getFlowId()).isEqualTo(addFlow.getFlowId());
        assertThat(flow.getTunnelId()).isEqualTo(addFlow.getTunnelId());
        assertThat(flow.getInnerTunnelId()).isEqualTo(addFlow.getInnerTunnelId());
        assertThat(flow.getDirection()).isEqualTo(FlowDirection.toBoolean(addFlow.getDirection()));
        assertThat(flow.getUdpSrcPort()).isEqualTo(flowRttUdpSrcPortOffset + addFlow.getPort());

        Map<Long, List<String>> vlanToSwitch = switchToVlanMapping.getVlan();

        vlanToSwitch.forEach((vlan, switches) -> {
            if (switches.contains(switchId)) {
                assertThat(flow.getTransitTunnelId()).isEqualTo(vlan);
            }
        });

        assertThat(flow.getDstMac()).isSubstringOf(switchId).isNotEqualTo(switchId);
    }

    @Test
    public void removeFlow() throws Exception {

        RemoveFlow removeFlow = RemoveFlow.builder()
                .flowId("some-flow-id")
                .build();

        gate.listen(removeFlow);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.REMOVE_FLOW);

        assertThat(commandPacket.getCommandList()).hasSize(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(FlowRttControl.RemoveFlow.class)).isTrue();

        FlowRttControl.RemoveFlow unpack = command.unpack(FlowRttControl.RemoveFlow.class);
        assertThat(unpack.getFlow().getFlowId()).isEqualTo(removeFlow.getFlowId());
    }


    @Test
    public void clearFlowsTest() throws Exception {
        Headers headers = Headers.builder().correlationId("some-correlation-id").build();
        ClearFlows clearFlows = ClearFlows.builder().headers(headers).build();

        String dpId = "00:00:1b:45:18:d6:71:5a";
        gate.listen(clearFlows, dpId);
        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.CLEAR_FLOWS);


        assertThat(commandPacket.getCommandList()).hasSize(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(FlowRttControl.ClearFlowsFilter.class)).isTrue();

        FlowRttControl.ClearFlowsFilter unpack = command.unpack(FlowRttControl.ClearFlowsFilter.class);
        String dstMac = "1b:45:18:d6:71:5a";
        assertThat(unpack.getDstMac()).isEqualTo(dstMac);
    }


    @Test
    public void listFlowsTest() throws Exception {

        Builder commandPacketResponseBuilded = CommandPacketResponse.newBuilder();

        Flow flow1 = Flow.newBuilder().setFlowId("some-flow-id-01").build();
        Flow flow2 = Flow.newBuilder().setFlowId("some-flow-id-02").build();

        commandPacketResponseBuilded.addResponse(Any.pack(flow1));
        commandPacketResponseBuilded.addResponse(Any.pack(flow2));

        CommandPacketResponse commandPacketResponse = commandPacketResponseBuilded.build();

        when(zeroMqClient.send(argThat(
                commandPacket -> commandPacket.getType() == Type.LIST_FLOWS)))
                .thenReturn(commandPacketResponse);


        String switchId = "00:00:1b:45:18:d6:71:5a";

        Headers headers = Headers.builder().correlationId("some-correlation-id").build();
        gate.listen(new ListFlowsRequest(headers), switchId);

        ArgumentCaptor<ListFlowsResponse> argument = ArgumentCaptor.forClass(ListFlowsResponse.class);
        verify(template).send(eq(toStorm), argument.capture());

        ListFlowsResponse response = argument.getValue();

        assertThat(response.getFlowIds()).contains(flow1.getFlowId(), flow2.getFlowId());
    }

    @Test
    public void pushSettingsTest() throws Exception {
        PushSettings data = PushSettings.builder()
                .packetGenerationIntervalInMs(500)
                .build();

        gate.listen(data);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.PUSH_SETTINGS);

        assertThat(commandPacket.getCommandList()).hasSize(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(Control.PushSettings.class)).isTrue();

        Control.PushSettings unpack = command.unpack(Control.PushSettings.class);
        assertThat(unpack.getPacketGenerationIntervalInMs()).isEqualTo(500);
    }

    @Test
    public void testAddIsl() throws Exception {
        AddIsl addIsl = AddIsl.builder()
                .switchId(new SwitchId("00:00:1b:45:18:d6:71:5a"))
                .port(42)
                .build();
        gate.listen(addIsl);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.ADD_ISL);

        assertThat(commandPacket.getCommandCount()).isEqualTo(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(IslRttControl.AddIsl.class)).isTrue();

        IslRttControl.AddIsl unpack = command.unpack(IslRttControl.AddIsl.class);
        IslRttControl.IslEndpoint endpoint = unpack.getIsl();

        String switchId = addIsl.getSwitchId().toString();
        assertThat(endpoint.getSwitchId()).isEqualTo(switchId);
        assertThat(endpoint.getPort()).isEqualTo(addIsl.getPort());
        assertThat(unpack.getUdpSrcPort()).isEqualTo(islRttUdpSrcPortOffset + addIsl.getPort());
        assertThat(unpack.getSwitchMac()).isSubstringOf(switchId).isNotEqualTo(switchId);

    }

    @Test
    public void testRemoveIsl() throws Exception {
        RemoveIsl removeIsl = RemoveIsl.builder()
                .switchId(new SwitchId("00:00:1b:45:18:d6:71:5a"))
                .port(42)
                .build();
        gate.listen(removeIsl);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.REMOVE_ISL);

        assertThat(commandPacket.getCommandList()).hasSize(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(IslRttControl.RemoveIsl.class)).isTrue();

        IslRttControl.RemoveIsl unpack = command.unpack(IslRttControl.RemoveIsl.class);
        IslRttControl.IslEndpoint endpoint = unpack.getIsl();
        String switchId = removeIsl.getSwitchId().toString();
        assertThat(endpoint.getSwitchId()).isEqualTo(switchId);
        assertThat(endpoint.getPort()).isEqualTo(removeIsl.getPort());
    }

    @Test
    public void testClearIsls() throws Exception {
        ClearIsls clearIsls = ClearIsls.builder()
                .switchId(new SwitchId("00:00:1b:45:18:d6:71:5a"))
                .build();
        gate.listen(clearIsls);

        CommandPacket commandPacket = getCommandPacket();
        assertThat(commandPacket.getType()).isEqualTo(Type.CLEAR_ISLS);

        assertThat(commandPacket.getCommandList()).hasSize(1);
        Any command = commandPacket.getCommand(0);
        assertThat(command.is(IslRttControl.ClearIslsFilter.class)).isTrue();

        IslRttControl.ClearIslsFilter unpack = command.unpack(IslRttControl.ClearIslsFilter.class);
        String switchId = clearIsls.getSwitchId().toString();
        assertThat(unpack.getSwitchId()).isEqualTo(switchId);
    }

    @Test
    public void listIslsTest() throws Exception {
        Builder commandPacketResponseBuilded = CommandPacketResponse.newBuilder();

        String switchId = "00:00:1b:45:18:d6:71:5a";
        IslEndpoint port1 = IslEndpoint.newBuilder().setSwitchId(switchId).setPort(1).build();
        IslEndpoint port2 = IslEndpoint.newBuilder().setSwitchId(switchId).setPort(2).build();
        IslEndpoint port3 = IslEndpoint.newBuilder().setSwitchId(switchId).setPort(3).build();
        IslEndpoint port4 = IslEndpoint.newBuilder().setSwitchId(switchId).setPort(4).build();

        commandPacketResponseBuilded.addResponse(Any.pack(port1));
        commandPacketResponseBuilded.addResponse(Any.pack(port2));
        commandPacketResponseBuilded.addResponse(Any.pack(port3));
        commandPacketResponseBuilded.addResponse(Any.pack(port4));

        CommandPacketResponse commandPacketResponse = commandPacketResponseBuilded.build();

        when(zeroMqClient.send(argThat(
                commandPacket -> commandPacket.getType() == Type.LIST_ISLS)))
                .thenReturn(commandPacketResponse);

        gate.listen(ListIslsRequest.builder().switchId(new SwitchId(switchId)).build());

        ArgumentCaptor<ListIslsResponse> argument = ArgumentCaptor.forClass(ListIslsResponse.class);
        verify(template).send(eq(toStorm), argument.capture());

        ListIslsResponse response = argument.getValue();

        assertThat(response.getPorts()).contains(port1.getPort(), port2.getPort(), port3.getPort(), port4.getPort());
    }

    private CommandPacket getCommandPacket() throws InvalidProtocolBufferException {
        ArgumentCaptor<CommandPacket> argument = ArgumentCaptor.forClass(CommandPacket.class);
        verify(zeroMqClient).send(argument.capture());
        return argument.getValue();
    }
}
