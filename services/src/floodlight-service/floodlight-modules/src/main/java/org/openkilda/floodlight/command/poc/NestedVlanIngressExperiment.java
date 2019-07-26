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
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NestedVlanIngressExperiment extends AbstractFlowCommand {
    @JsonCreator
    public NestedVlanIngressExperiment(@JsonProperty("message_context") MessageContext messageContext,
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
        final OFFactory of = sw.getOFFactory();
        SwitchDescriptor swDesc = new SwitchDescriptor(sw);
        log.info("switch description: {}", swDesc);

        List<OFMessage> rules = new ArrayList<>();
        if (0 < outerVlan) {
            if (0 < innerVlan) {
                rules.add(makeOuterVlanRule(of, swDesc));
                rules.add(makeInnerVlanRule(of, swDesc));
            } else {
                rules.add(makeOuterOnlyVlanRule(of, swDesc));
            }
        } else {
            rules.add(makeDefaultPortRule(of, swDesc));
        }

        rules.add(makeArpReinject(of, swDesc));
        rules.add(makeAppCopyCatch(of, swDesc));
        rules.add(makeIngressForwarding(of, swDesc));

        return ImmutableList.of(
                new BatchWriter(rules.toArray(new OFMessage[0])));
    }

    private OFMessage makeOuterOnlyVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(PRIORITY_FLOW)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(outerVlan))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeMetadata(METADATA_FLOW_MATCH_MARK.or(cookie),
                                                        METADATA_FLOW_MATCH_MARK.or(METADATA_FLOW_MATCH_MASK)),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFMessage makeOuterVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(PRIORITY_FLOW)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(outerVlan))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.TABLE).build())),
                        of.instructions().writeMetadata(METADATA_OUTER_VLAN_MARK.or(U64.of(outerVlan)),
                                                        METADATA_OUTER_VLAN_MARK.or(METADATA_VLAN_MASK))))
                .build();
    }

    private OFMessage makeInnerVlanRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(PRIORITY_FLOW + 10)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(innerVlan))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(METADATA_OUTER_VLAN_MARK.or(U64.of(outerVlan))),
                                             OFMetadata.of(METADATA_OUTER_VLAN_MARK.or(METADATA_VLAN_MASK)))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                        of.instructions().writeMetadata(METADATA_FLOW_MATCH_MARK.or(cookie),
                                                        METADATA_FLOW_MATCH_MARK.or(METADATA_FLOW_MATCH_MASK)),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFMessage makeDefaultPortRule(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePreIngress())
                .setPriority(PRIORITY_FLOW - 10)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(inPort))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().writeMetadata(METADATA_FLOW_MATCH_MARK.or(cookie),
                                                        METADATA_FLOW_MATCH_MARK.or(METADATA_FLOW_MATCH_MASK)),
                        of.instructions().gotoTable(swDesc.getTableIngress())))
                .build();
    }

    private OFMessage makeArpReinject(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress())
                .setPriority(PRIORITY_FLOW)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(METADATA_FLOW_MATCH_MARK.or(cookie)),
                                             OFMetadata.of(METADATA_FLOW_MATCH_MARK.or(METADATA_FLOW_MATCH_MASK)))
                                  .setExact(MatchField.ETH_TYPE, EthType.ARP)
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.TABLE).build())),
                        of.instructions().gotoTable(swDesc.getTablePostIngress())))
                .build();
    }

    private OFMessage makeAppCopyCatch(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTableIngress())
                .setPriority(PRIORITY_REINJECT)
                .setCookie(cookie)
                .setMatch(
                        of.buildMatch()
                                .setMasked(MatchField.METADATA,
                                           OFMetadata.of(METADATA_FLOW_MATCH_MARK
                                                                 .or(METADATA_APP_COPY_MARK)
                                                                 .or(cookie)),
                                           OFMetadata.of(METADATA_FLOW_MATCH_MARK
                                                                 .or(METADATA_APP_COPY_MARK)
                                                                 .or(METADATA_FLOW_MATCH_MASK)))
                                .build())
                // TODO - meter
                .setInstructions(ImmutableList.of(
                        of.instructions().writeActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.CONTROLLER).build()))))
                .build();
    }

    private OFMessage makeIngressForwarding(OFFactory of, SwitchDescriptor swDesc) {
        return of.buildFlowAdd()
                .setTableId(swDesc.getTablePostIngress())
                .setPriority(PRIORITY_FLOW)
                .setCookie(cookie)
                .setMatch(of.buildMatch()
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(METADATA_FLOW_MATCH_MARK.or(cookie)),
                                             OFMetadata.of(METADATA_FLOW_MATCH_MARK.or(METADATA_FLOW_MATCH_MASK)))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().applyActions(ImmutableList.of(
                                of.actions().pushVlan(EthType.VLAN_FRAME),
                                of.actions().setField(of.oxms().vlanVid(OFVlanVidMatch.ofVlan(transitVlan)))/*,
                                of.actions().buildOutput().setPort(OFPort.of(outPort)).build()*/)),
                        // (FIXME) We should use write-action here... but it do not work, at least on OVS.
                        of.instructions().writeActions(ImmutableList.of(
                                of.actions().buildOutput().setPort(OFPort.of(outPort)).build()))))
                .build();
    }
}
