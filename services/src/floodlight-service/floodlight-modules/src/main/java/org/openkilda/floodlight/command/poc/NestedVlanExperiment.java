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

package org.openkilda.floodlight.command.poc;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.BatchWriter;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NestedVlanExperiment extends SpeakerCommand {
    private static final int RULE_PRIORITY = FlowModUtils.PRIORITY_HIGH;
    private static final U64 COOKIE = U64.of(0x2140001L);

    private final short outerVlan;
    private final short innerVlan;

    @JsonCreator
    public NestedVlanExperiment(@JsonProperty("message_context") MessageContext messageContext,
                                @JsonProperty("switch_id") SwitchId switchId,
                                @JsonProperty("outer_vlan") short outerVlan,
                                @JsonProperty("inner_vlan") short innerVlan) {
        super(switchId, messageContext);
        this.outerVlan = outerVlan;
        this.innerVlan = innerVlan;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        return ImmutableList.of(
                makeDummyMatches(swDesc),
                makeExperiment(swDesc));
    }

    private BatchWriter makeDummyMatches(SwitchDescriptor swDesc) {
        OFFactory of = swDesc.getSw().getOFFactory();
        return new BatchWriter(of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress1())
                .setPriority(1)
                .setCookie(COOKIE)
                .build());
    }

    private BatchWriter makeExperiment(SwitchDescriptor swDesc) {
        OFFactory of = swDesc.getSw().getOFFactory();

        List<OFAction> applyActions = new ArrayList<>();
        applyActions.add(of.actions().popVlan());

        List<OFInstruction> instructions = new ArrayList<>();
        instructions.add(of.instructions().applyActions(applyActions));
        instructions.add(of.instructions().writeMetadata(U64.of(0x1ffeL), U64.of(0x1fff)));
        instructions.add(of.instructions().gotoTable(swDesc.getTableIngress0()));

        OFMessage rule = of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress1())
                .setPriority(RULE_PRIORITY)
                .setCookie(COOKIE)
                .setInstructions(instructions)
                .build();

        return new BatchWriter(rule);
    }

    @Getter
    private static class SwitchDescriptor {
        private final IOFSwitch sw;

        private final TableId tableIngress0;
        private final TableId tableIngress1;

        public SwitchDescriptor(IOFSwitch sw) {
            this.sw = sw;

            Iterator<TableId> tablesIterator = sw.getTables().iterator();
            tableIngress0 = tablesIterator.next();
            tableIngress1 = tablesIterator.next();
        }
    }
}
