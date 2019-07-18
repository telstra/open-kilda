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
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;

public class MultiTableMarkEndpointAsIsl extends AbstractMultiTableCommand {
    protected static final U64 COOKIE = U64.of(0x2140002L);

    private final int islPort;

    @JsonCreator
    public MultiTableMarkEndpointAsIsl(@JsonProperty("message_context") MessageContext messageContext,
                                       @JsonProperty("switch_id") SwitchId switchId,
                                       @JsonProperty("isl_port") int islPort) {
        super(switchId, messageContext);
        this.islPort = islPort;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        return ImmutableList.of(new BatchWriter(
                makeIslRedirect(of, swDesc)));
    }

    private OFMessage makeIslRedirect(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableDispatch())
                .setPriority(PRIORITY_ISL_EGRESS)
                .setCookie(COOKIE)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(islPort))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().gotoTable(swDesc.getTableEgress())))
                .build();
    }
}
