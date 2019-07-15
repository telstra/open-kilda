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
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
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
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.Set;

public abstract class IngressFlowModFactory {
    @VisibleForTesting
    @Getter(AccessLevel.MODULE)
    final IngressFlowSegmentBase command;

    protected final Set<SwitchFeature> switchFeatures;

    protected final IOFSwitch sw;

    protected final OFFactory of;

    protected final OfFlowModBuilderFactory flowModBuilderFactory;

    public IngressFlowModFactory(
            OfFlowModBuilderFactory flowModBuilderFactory, IngressFlowSegmentBase command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        this.flowModBuilderFactory = flowModBuilderFactory;
        this.command = command;
        this.sw = sw;
        this.switchFeatures = features;

        of = sw.getOFFactory();
    }

    /**
     * Make rule to match traffic by port+vlan and route it into ISL/egress end.
     */
    public OFFlowMod makeOuterVlanOnlyForwardMessage(MeterId effectiveMeterId) {
        FlowEndpoint endpoint = command.getEndpoint();
        OFFlowMod.Builder builder = flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID))
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), endpoint.getVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    /**
     * Make rule to match whole port traffic and route it into ISL/egress end.
     */
    public OFFlowMod makeDefaultPortFlowMatchAndForwardMessage(MeterId effectiveMeterId) {
        // FIXME we need some space between match rules (so it should be -10 instead of -1)
        OFFlowMod.Builder builder = flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.INGRESS_TABLE_ID),
                                                                      -1)
                .setCookie(U64.of(command.getCookie().getValue()))
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                        .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
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

    private OFFlowMod makeForwardMessage(OFFactory of, OFFlowMod.Builder builder, MeterId effectiveMeterId) {
        builder.setInstructions(makeForwardMessageInstructions(of, effectiveMeterId));
        if (switchFeatures.contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    protected abstract List<OFInstruction> makeForwardMessageInstructions(OFFactory of, MeterId effectiveMeterId);
}
