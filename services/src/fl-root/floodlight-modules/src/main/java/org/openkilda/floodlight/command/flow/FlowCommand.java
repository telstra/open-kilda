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

package org.openkilda.floodlight.command.flow;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;

import org.openkilda.floodlight.command.OfCommand;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
public abstract class FlowCommand extends OfCommand {
    // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid mask
    // 0x1FFF lead to rule reject during install attempt on accton based switches.
    private static short OF10_VLAN_MASK = 0x0FFF;
    protected static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;
    protected static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    String flowId;
    Long cookie;

    FlowCommand(String flowId, MessageContext messageContext, Long cookie, SwitchId switchId) {
        super(switchId, messageContext);
        this.flowId = flowId;
        this.cookie = cookie;
    }

    final Match matchFlow(Integer inputPort, Integer transitVlanId, OFFactory ofFactory) {
        Match.Builder mb = ofFactory.buildMatch();
        mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        if (transitVlanId > 0) {
            matchVlan(ofFactory, mb, transitVlanId);
        }

        return mb.build();
    }

    final void matchVlan(OFFactory ofFactory, Match.Builder matchBuilder, int vlanId) {
        if (OF_12.compareTo(ofFactory.getVersion()) >= 0) {
            matchBuilder.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId),
                    OFVlanVidMatch.ofRawVid(OF10_VLAN_MASK));
        } else {
            matchBuilder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        }
    }

    final CompletableFuture<List<OFFlowStatsEntry>> dumpFlowTable(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();
        OFFlowStatsRequest flowRequest = ofFactory.buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(U64.ZERO)
                .build();

        CompletableFuture<List<OFFlowStatsReply>> dumpFlowStatsStage =
                new CompletableFutureAdapter<>(sw.writeStatsRequest(flowRequest));
        return dumpFlowStatsStage.thenApply(values ->
                values.stream()
                        .map(OFFlowStatsReply::getEntries)
                        .flatMap(List::stream)
                        .collect(Collectors.toList())
        );
    }
}
