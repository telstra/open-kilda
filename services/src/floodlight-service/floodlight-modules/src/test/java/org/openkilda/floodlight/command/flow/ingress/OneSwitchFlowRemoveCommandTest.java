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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.UUID;

public class OneSwitchFlowRemoveCommandTest extends IngressFlowSegmentRemoveTest {
    private static final FlowEndpoint endpointEgressDefaultPort = new FlowEndpoint(
            endpointIngresDefaultPort.getDatapath(),
            IngressFlowSegmentRemoveTest.endpointEgressDefaultPort.getPortNumber(),
            IngressFlowSegmentRemoveTest.endpointEgressDefaultPort.getVlanId());
    private static final FlowEndpoint endpointEgressSingleVlan = new FlowEndpoint(
            endpointIngressSingleVlan.getDatapath(),
            IngressFlowSegmentRemoveTest.endpointEgressSingleVlan.getPortNumber(),
            IngressFlowSegmentRemoveTest.endpointEgressSingleVlan.getVlanId());

    @Test
    public void happyPathDefaultPort() throws Exception {
        OneSwitchFlowRemoveCommand command = getCommandBuilder()
                .endpoint(endpointIngresDefaultPort)
                .build();
        executeCommand(command, 1);

        OFFlowDeleteStrict expect = of.buildFlowDeleteStrict()
                .setPriority(OneSwitchFlowRemoveCommand.FLOW_PRIORITY - 1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, getWriteRecord(0).getRequest());
    }

    @Test
    public void happyPathOuterVlan() throws Exception {
        OneSwitchFlowRemoveCommand command = getCommandBuilder().endpoint(endpointIngressSingleVlan).build();
        executeCommand(command, 1);

        verifyOuterVlanMatchRemove(command, getWriteRecord(0).getRequest());

        OFFlowDeleteStrict expected = of.buildFlowDeleteStrict()
                .setPriority(OneSwitchFlowRemoveCommand.FLOW_PRIORITY)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expected, getWriteRecord(0).getRequest());
    }

    @Override
    protected CommandBuilder getCommandBuilder() {
        return new CommandBuilder();
    }

    static class CommandBuilder implements ICommandBuilder {
        private final FlowSegmentMetadata metadata = new FlowSegmentMetadata(
                "single-switch-flow-remove-flow-id", new Cookie(1), false);

        private FlowEndpoint endpoint = OneSwitchFlowRemoveCommandTest.endpointIngressSingleVlan;
        private FlowEndpoint egressEndpoint = OneSwitchFlowRemoveCommandTest.endpointEgressSingleVlan;
        private MeterConfig meterConfig = OneSwitchFlowRemoveCommandTest.meterConfig;

        @Override
        public OneSwitchFlowRemoveCommand build() {
            return new OneSwitchFlowRemoveCommand(
                    new MessageContext(), UUID.randomUUID(), metadata, endpoint, meterConfig, egressEndpoint);
        }

        @Override
        public CommandBuilder endpoint(FlowEndpoint endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public CommandBuilder egressEndpoint(FlowEndpoint endpoint) {
            this.egressEndpoint = endpoint;
            return this;
        }

        @Override
        public CommandBuilder meterConfig(MeterConfig meterConfig) {
            this.meterConfig = meterConfig;
            return this;
        }
    }
}
