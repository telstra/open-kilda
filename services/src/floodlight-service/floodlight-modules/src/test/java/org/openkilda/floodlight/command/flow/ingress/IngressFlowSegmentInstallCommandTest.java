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

import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallMultiTableFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallSingleTableFlowModFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.UUID;

public class IngressFlowSegmentInstallCommandTest extends IngressCommandInstallTest {
    @Test
    public void zeroVlanSingleTable() throws Exception {
        processZeroVlanSingleTable(makeCommand(
                endpointIngressZeroVlan, endpointEgressOneVlan, meterConfig, makeMetadata(false)));
    }

    @Test
    public void oneVlanSingleTable() throws Exception {
        processOneVlanSingleTable(makeCommand(
                endpointIngressOneVlan, endpointEgressOneVlan, meterConfig, makeMetadata(false)));
    }

    @Test
    public void zeroVlanMultiTable() throws Exception {
        processZeroVlanMultiTable(makeCommand(
                endpointIngressZeroVlan, endpointEgressOneVlan, meterConfig, makeMetadata(true)));
    }

    @Test
    public void oneVlanMultiTable() throws Exception {
        processOneVlanMultiTable(makeCommand(
                endpointIngressOneVlan, endpointEgressOneVlan, meterConfig, makeMetadata(true)));
    }

    @Override
    protected void switchFeaturesSetup(IOFSwitch target, Set<SwitchFeature> features) {
        features.add(SwitchFeature.NOVIFLOW_COPY_FIELD);
        super.switchFeaturesSetup(target, features);
    }

    @Override
    protected void expectMeter() {
        expectMeterInstall();
    }

    @Override
    protected IngressFlowSegmentBase makeCommand(
            FlowEndpoint endpoint, MeterConfig meterConfig, FlowSegmentMetadata metadata) {
        return makeCommand(endpoint, endpointEgressOneVlan, meterConfig, metadata);
    }

    protected IngressFlowSegmentInstallCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint egressEndpoint, MeterConfig meterConfig, FlowSegmentMetadata metadata) {
        UUID commandId = UUID.randomUUID();
        return new CommandStub(
                new MessageContext(commandId.toString()), commandId, metadata, endpoint, meterConfig,
                egressEndpoint.getSwitchId(), 6, encapsulationVlan, false);
    }

    static class CommandStub extends IngressFlowSegmentInstallCommand implements IFlowModFactoryOverride {
        @Getter
        private IngressFlowModFactory realFlowModFactory;

        public CommandStub(
                MessageContext context, UUID commandId, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
                MeterConfig meterConfig, SwitchId egressSwitchId, Integer islPort,
                FlowTransitEncapsulation encapsulation, boolean removeCustomerPortSharedCatchRule) {
            super(context, commandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                    removeCustomerPortSharedCatchRule);
        }

        @Override
        protected void setupFlowModFactory() {
            super.setupFlowModFactory();

            realFlowModFactory = getFlowModFactory();
            if (metadata.isMultiTable()) {
                Assert.assertTrue(realFlowModFactory instanceof IngressFlowSegmentInstallMultiTableFlowModFactory);
            } else {
                Assert.assertTrue(realFlowModFactory instanceof IngressFlowSegmentInstallSingleTableFlowModFactory);
            }

            setFlowModFactory(flowModFactoryMock);
        }
    }
}
