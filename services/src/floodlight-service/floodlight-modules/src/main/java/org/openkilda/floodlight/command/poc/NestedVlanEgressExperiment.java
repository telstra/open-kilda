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
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.ArrayList;
import java.util.List;

public class NestedVlanEgressExperiment extends AbstractFlowCommand {
    @JsonCreator
    public NestedVlanEgressExperiment(@JsonProperty("message_context") MessageContext messageContext,
                                      @JsonProperty("cookie") long cookie,
                                      @JsonProperty("switch_id") SwitchId switchId,
                                      @JsonProperty("in_port") int inPort,
                                      @JsonProperty("out_port") int outPort,
                                      @JsonProperty("outer_vlan") short outerVlan,
                                      @JsonProperty("inner_vlan") short innerVlan,
                                      @JsonProperty("transit_vlan") short transitVlan) {
        super(messageContext, cookie, switchId, inPort, outPort, outerVlan, innerVlan, transitVlan);
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        return ImmutableList.of(new BatchWriter(makeFlowEgressRoute(of, swDesc)));
    }

    OFMessage makeFlowEgressRoute(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableEgress())
                .setPriority(PRIORITY_FLOW)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(transitVlan))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(applyTransitDecode(of)),
                        of.instructions().writeActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.of(outPort)).build()))))
                .build();
    }

    private List<OFAction> applyTransitDecode(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (0 < innerVlan) {
            actions.add(of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofVlan(innerVlan))));
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
            actions.add(of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofVlan(outerVlan))));
        } else if (0 < outerVlan) {
            actions.add(of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofVlan(outerVlan))));
        } else {
            actions.add(of.actions().popVlan());
        }

        return actions;
    }
}
