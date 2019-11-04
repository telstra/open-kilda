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

import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentBase;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class IngressInstallFlowModFactory extends IngressFlowModFactory {
    public IngressInstallFlowModFactory(
            OfFlowModBuilderFactory.Factory flowModFactoryFactory, IngressFlowSegmentBase command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        super(flowModFactoryFactory.actionAdd(), command, sw, features);
    }

    @Override
    protected List<OFInstruction> makeOuterVlanMatchMessageInstructions() {
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(command.getEndpoint().getOuterVlanId()));
        return ImmutableList.of(
                of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                of.instructions().gotoTable(TableId.of(SwitchManager.INGRESS_TABLE_ID)));
    }

    protected List<OFInstruction> makeForwardMessageInstructions(
            OFFactory of, MeterId effectiveMeterId, List<Integer> vlanStack) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, effectiveMeterId, applyActions, instructions);
        }

        applyActions.addAll(makeTransformActions(vlanStack));
        applyActions.add(makeOutputAction());

        instructions.add(of.instructions().applyActions(applyActions));
        if (command.getEndpoint().isTrackConnectedDevices()) {
            instructions.add(of.instructions().gotoTable(TableId.of(SwitchManager.POST_INGRESS_TABLE_ID)));
        }

        return instructions;
    }

    protected abstract List<OFAction> makeTransformActions(List<Integer> vlanStack);

    protected abstract OFAction makeOutputAction();

    protected final OFAction makeOutputAction(OFPort port) {
        return of.actions().buildOutput()
                .setPort(port)
                .build();
    }
}
