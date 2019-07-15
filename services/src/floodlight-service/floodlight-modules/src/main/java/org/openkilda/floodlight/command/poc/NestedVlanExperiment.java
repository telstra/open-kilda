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
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NestedVlanExperiment extends SpeakerCommand {
    private static final int RULE_PRIORITY = FlowModUtils.PRIORITY_HIGH;
    private static final U64 COOKIE = U64.of(0x2140002L);
    private static final U64 METADATA_2VLAN_MASk = U64.of(0xfff_fff);

    private final int inPort;
    private final int outPort;
    private final short outerVlan;
    private final short innerVlan;

    @JsonCreator
    public NestedVlanExperiment(@JsonProperty("message_context") MessageContext messageContext,
                                @JsonProperty("switch_id") SwitchId switchId,
                                @JsonProperty("in_port") int inPort,
                                @JsonProperty("out_port") int outPort,
                                @JsonProperty("outer_vlan") short outerVlan,
                                @JsonProperty("inner_vlan") short innerVlan) {
        super(switchId, messageContext);
        this.inPort = inPort;
        this.outPort = outPort;
        this.outerVlan = outerVlan;
        this.innerVlan = innerVlan;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        final OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        log.info("switch description: {}", swDesc);

        List<OFMessage> rules = new ArrayList<>();
        if (0 < outerVlan) {
            rules.add(makePreIngressVlanRule(of, swDesc));
            rules.add(makeIngressOuterVlanRule(of, swDesc));
            if (0 < innerVlan) {
                rules.add(makeIngressInnerVlanRule(of, swDesc));
            }
        } else {
            rules.add(makeIngressDefaultPortRule(of, swDesc));
        }

        return ImmutableList.of(
                new BatchWriter(rules.toArray(new OFMessage[0])));
    }

    private OFMessage makePreIngressVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(RULE_PRIORITY)
                .setCookie(COOKIE)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(outerVlan))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeMetadata(U64.of(outerVlan), METADATA_2VLAN_MASk),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFMessage makeIngressInnerVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return null; // TODO
    }

    private OFMessage makeIngressOuterVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress())
                .setPriority(RULE_PRIORITY)
                .setCookie(COOKIE)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(U64.of(outerVlan)), OFMetadata.of(METADATA_2VLAN_MASk))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().writeActions(pushTransitEncapsulation(of)),
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.of(outPort)).build())),
                        of.instructions().gotoTable(swDesc.getTablePostIngress())))
                .build();
    }

    private OFMessage makeIngressDefaultPortRule(OFFactory of, SwitchDescriptor swDesc) {
        return null; // TODO
    }

    private List<OFAction> pushTransitEncapsulation(OFFactory of) {
        return ImmutableList.of(); // TODO
    }
}
