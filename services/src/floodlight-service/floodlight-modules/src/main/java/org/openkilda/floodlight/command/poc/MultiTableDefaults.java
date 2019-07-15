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
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;

public class MultiTableDefaults extends SpeakerCommand {
    private static final U64 COOKIE = U64.of(0x2140001L);

    @JsonCreator
    public MultiTableDefaults(@JsonProperty("switch_id") SwitchId switchId,
                              @JsonProperty("message_context") MessageContext messageContext) {
        super(switchId, messageContext);
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        return ImmutableList.of(new BatchWriter(
                makeDispatchMissing(of, swDesc),
                makePreIngressMissing(of, swDesc),
                makeIngressMissing(of, swDesc),
                makePostIngressMissing(of, swDesc)));
    }

    private OFMessage makeDispatchMissing(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableDispatch())
                .setPriority(0)
                .setCookie(COOKIE)
                .setInstructions(ImmutableList.of(
                        of.instructions().gotoTable(swDesc.getTablePreIngress())))
                .build();
    }

    private OFMessage makePreIngressMissing(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(0)
                .setCookie(COOKIE)
                .setInstructions(ImmutableList.of(
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFMessage makeIngressMissing(OFFactory of, SwitchDescriptor swDesc) {
        return makeDropAllMissing(of)
                .setTableId(swDesc.getTableIngress())
                .build();
    }

    private OFMessage makePostIngressMissing(OFFactory of, SwitchDescriptor swDesc) {
        return makeDropAllMissing(of)
                .setTableId(swDesc.getTablePostIngress())
                .build();
    }

    private OFFlowAdd.Builder makeDropAllMissing(OFFactory of) {
        return of.buildFlowAdd()
                .setPriority(0)
                .setCookie(COOKIE);
    }
}
