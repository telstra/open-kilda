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
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.IngressSegmentCookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowMod.Builder;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Set;

public abstract class IngressFlowModFactory {
    @VisibleForTesting
    @Getter(AccessLevel.MODULE)
    final IngressFlowSegmentBase command;

    protected final IngressSegmentCookie cookie;

    protected final Set<SwitchFeature> switchFeatures;

    protected final IOFSwitch sw;

    protected final OFFactory of;

    protected final OfFlowModBuilderFactory flowModBuilderFactory;

    public IngressFlowModFactory(
            OfFlowModBuilderFactory.Factory flowModFactoryFactory, IngressFlowSegmentBase command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        this.flowModBuilderFactory = flowModFactoryFactory
                .basePriority(SwitchManager.FLOW_PRIORITY)
                .make();
        this.command = command;
        this.sw = sw;
        this.switchFeatures = features;

        cookie = IngressSegmentCookie.convert(command.getCookie());
        of = sw.getOFFactory();
    }

    /**
     * Make rule to match outer VLAN tag, write it into metadata field and remove VLAN tag from packet.
     */
    public OFFlowMod makeOuterVlanMatchAndRemoveMessage() {
        FlowEndpoint endpoint = command.getEndpoint();
        final IngressSegmentCookie effectiveCookie = this.cookie.setSubType(
                IngressSegmentCookie.IngressSegmentSubType.OUTER_VLAN_MATCH_AND_REMOVE);
        return flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))
                .setCookie(U64.of(effectiveCookie.getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()))
                                  .build())
                .setInstructions(makeOuterVlanMatchMessageInstructions())
                .build();
    }

    /**
     * Make rule to match traffic by port+vlan and route it into ISL/egress end.
     */
    public OFFlowMod makeOuterOnlyVlanForwardMessage(MeterId effectiveMeterId) {
        FlowEndpoint endpoint = command.getEndpoint();
        OFFlowMod.Builder builder = flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), endpoint.getOuterVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(
                of, builder, IngressSegmentCookie.IngressSegmentSubType.OUTER_VLAN_ONLY, effectiveMeterId,
                endpoint.getVlanStack());
    }

    /**
     * Make rule to forward traffic matched by outer VLAN tag and forward in in ISL (or out port in case one-switch
     * flow).
     */
    public OFFlowMod makeSingleVlanForwardMessage(MeterId effectiveMeterId) {
        FlowEndpoint endpoint = command.getEndpoint();
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = flowModBuilderFactory
                .makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID), -10)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(
                of, builder, IngressSegmentCookie.IngressSegmentSubType.SINGLE_VLAN_FORWARD, effectiveMeterId,
                FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()));
    }

    /**
     * Make rule to match inner VLAN tag and forward in in ISL (or out port in case one-switch flow).
     */
    public OFFlowMod makeDoubleVlanForwardMessage(MeterId effectiveMeterId) {
        FlowEndpoint endpoint = command.getEndpoint();
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        OFFlowMod.Builder builder = flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getInnerVlanId()))
                                  .setMasked(MatchField.METADATA,
                                             OFMetadata.of(metadata.getValue()), OFMetadata.of(metadata.getMask()))
                                  .build());
        return makeForwardMessage(
                of, builder, IngressSegmentCookie.IngressSegmentSubType.DOUBLE_VLAN_FORWARD, effectiveMeterId,
                FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()));
    }

    /**
     * Make rule to match whole port traffic and route it into ISL/egress end.
     */
    public OFFlowMod makeDefaultPortForwardMessage(MeterId effectiveMeterId) {
        // FIXME we need some space between match rules (so it should be -10 instead of -1)
        OFFlowMod.Builder builder = flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID),
                                                                      -1)
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build());
        return makeForwardMessage(
                of, builder, IngressSegmentCookie.IngressSegmentSubType.DEFAULT_PORT_FORWARD, effectiveMeterId,
                command.getEndpoint().getVlanStack());
    }

    /**
     * Route all traffic for specific physical port into pre-ingress table. Shared across all flows for this physical
     * port (if OF flow-mod ADD message use same match and priority fields with existing OF flow, existing OF flow will
     * be replaced/not added).
     */
    public OFFlowMod makeCustomerPortSharedCatchInstallMessage() {
        FlowEndpoint endpoint = command.getEndpoint();
        return of.buildFlowAdd()
                .setTableId(TableId.of(SwitchManager.INPUT_TABLE_ID))
                .setPriority(SwitchManager.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE)
                .setCookie(U64.of(Cookie.encodeIngressRulePassThrough(endpoint.getPortNumber())))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build())
                .setInstructions(ImmutableList.of(
                        of.instructions().gotoTable(TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))))
                .build();
    }

    /**
     * Make remove request for ingress rule in old (pre QinQ) format.
     */
    public OFFlowMod makeOldForwardingRemoveMessage(int priorityOffset) {
        OFFlowMod.Builder builder = of.buildFlowDelete();
        builder = flowModBuilderFactory.setTableId(builder, TableId.of(SwitchManager.INGRESS_TABLE_ID));
        builder = flowModBuilderFactory.setPriority(builder, priorityOffset);
        return builder
                .setCookie(U64.of(command.getCookie().getValue()))
                .setCookieMask(U64.NO_MASK)
                .build();
    }

    private OFFlowMod makeForwardMessage(
            OFFactory of, Builder builder, IngressSegmentCookie.IngressSegmentSubType subType,
            MeterId effectiveMeterId, List<Integer> vlanStack) {
        builder.setCookie(U64.of(cookie.setForwarding(true).setSubType(subType).getValue()))
                .setInstructions(makeForwardMessageInstructions(of, effectiveMeterId, vlanStack));
        if (switchFeatures.contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    protected abstract List<OFInstruction> makeOuterVlanMatchMessageInstructions();

    protected abstract List<OFInstruction> makeForwardMessageInstructions(
            OFFactory of, MeterId effectiveMeterId, List<Integer> vlanStack);
}
