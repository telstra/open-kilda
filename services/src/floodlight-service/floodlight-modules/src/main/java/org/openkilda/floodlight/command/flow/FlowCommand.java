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

import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.OfCommand;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.errormsg.OFFlowModFailedErrorMsg;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
public abstract class FlowCommand extends OfCommand {
    // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid mask
    // 0x1FFF lead to rule reject during install attempt on accton based switches.
    private static short OF10_VLAN_MASK = 0x0FFF;
    protected static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;
    protected static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    final UUID commandId;
    final String flowId;
    final Cookie cookie;

    FlowCommand(UUID commandId, String flowId, MessageContext messageContext, Cookie cookie, SwitchId switchId) {
        super(switchId, messageContext);
        this.commandId = commandId;
        this.flowId = flowId;
        this.cookie = cookie;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        ErrorCode code;
        if (error instanceof SwitchNotFoundException) {
            code = ErrorCode.SWITCH_UNAVAILABLE;
        } else if (error instanceof SessionErrorResponseException) {
            SessionErrorResponseException sessionException = (SessionErrorResponseException) error;
            OFErrorMsg errorMsg = sessionException.getErrorResponse();
            OFFlowModFailedErrorMsg modFailedError = (OFFlowModFailedErrorMsg) errorMsg;
            code = getCode(modFailedError);
        } else {
            code = ErrorCode.UNKNOWN;
        }

        return FlowErrorResponse.errorBuilder()
                .errorCode(code)
                .description(error.getMessage())
                .messageContext(messageContext)
                .switchId(switchId)
                .commandId(commandId)
                .flowId(flowId)
                .build();
    }

    final Match matchFlow(Integer inputPort, Integer tunnelId, FlowEncapsulationType encapsulationType,
                          MacAddress ethSrc, OFFactory ofFactory) {
        Match.Builder mb = ofFactory.buildMatch();
        mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        if (tunnelId > 0) {
            switch (encapsulationType) {
                case TRANSIT_VLAN:
                    matchVlan(ofFactory, mb, tunnelId);
                    break;
                case VXLAN:
                    matchVxlan(ofFactory, mb, tunnelId, ethSrc);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown encapsulation type: %s", encapsulationType));
            }
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

    final void matchVxlan(OFFactory ofFactory, Match.Builder matchBuilder, long tunnelId,
                               MacAddress ethSrc) {
        if (ethSrc != null) {
            matchBuilder.setExact(MatchField.ETH_SRC, ethSrc);
        }
        if (OF_12.compareTo(ofFactory.getVersion()) >= 0) {
            throw new UnsupportedOperationException("Switch doesn't support tunnel_id match");
        } else {
            matchBuilder.setExact(MatchField.TUNNEL_ID, U64.of(tunnelId));
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

    private ErrorCode getCode(OFFlowModFailedErrorMsg error) {
        switch (error.getCode()) {
            case UNSUPPORTED:
                return ErrorCode.UNSUPPORTED;
            case BAD_COMMAND:
                return ErrorCode.BAD_COMMAND;
            case BAD_FLAGS:
                return ErrorCode.BAD_FLAGS;
            default:
                return ErrorCode.UNKNOWN;
        }
    }
}
