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

package org.openkilda.floodlight.command.flow.ingress.of;

import org.openkilda.floodlight.command.flow.ingress.OneSwitchFlowInstallCommand;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;

import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.types.TableId;

import java.util.Optional;

public class OneSwitchFlowInstallMultiTableFlowModFactoryTest extends OneSwitchFlowInstallFlowModFactoryTest {

    @Override
    IngressFlowModFactory makeFactory(OneSwitchFlowInstallCommand command) {
        return new OneSwitchFlowInstallMultiTableFlowModFactory(command, sw, switchFeatures);
    }

    @Override
    FlowSegmentMetadata makeMetadata() {
        return new FlowSegmentMetadata(flowId, cookie);
    }

    @Override
    TableId getTargetPreIngressTableId() {
        return TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID);
    }

    @Override
    TableId getTargetIngressTableId() {
        return TableId.of(SwitchManager.INGRESS_TABLE_ID);
    }

    @Override
    Optional<OFInstructionGotoTable> getGoToTableInstruction() {
        return Optional.of(of.instructions().gotoTable(TableId.of(SwitchManager.POST_INGRESS_TABLE_ID)));
    }

    @Override
    Optional<OFInstructionWriteMetadata> getWriteMetadataInstruction() {
        RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build(switchFeatures);
        return Optional.of(of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()));
    }
}
