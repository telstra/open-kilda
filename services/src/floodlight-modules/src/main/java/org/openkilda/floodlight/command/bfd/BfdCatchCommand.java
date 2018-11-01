/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.command.bfd;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.floodlight.response.BfdCatchResponse;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.NoviBfdCatch;
import org.openkilda.messaging.model.NoviBfdCatch.Errors;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

abstract class BfdCatchCommand extends BfdCommand {
    // One of verification rule is matched only be our DPID in ethDst field. So it will catch all our
    // packages it our catch will be below it.
    protected static final int CATCH_RULES_PRIORITY = SwitchManager.VERIFICATION_RULE_PRIORITY + 10;

    private final NoviBfdCatch bfdCatch;
    private final BfdCatchResponse.BfdCatchResponseBuilder responseBuilder;

    public BfdCatchCommand(CommandContext context, NoviBfdCatch bfdCatch) {
        super(context, DatapathId.of(bfdCatch.getTarget().getDatapath().toLong()));

        this.bfdCatch = bfdCatch;
        this.responseBuilder = BfdCatchResponse.builder()
                .bfdCatch(bfdCatch);
    }

    @Override
    protected InfoData assembleResponse() {
        return responseBuilder.build();
    }

    @Override
    protected void errorDispatcher(Throwable error) throws Throwable {
        try {
            super.errorDispatcher(error);
        } catch (Throwable e) {
            responseBuilder.errorCode(Errors.SWITCH_RESPONSE_ERROR);
            throw e;
        }
    }

    protected OFFlowMod.Builder applyCommonFlowModSettings(IOFSwitch sw, OFFlowMod.Builder messageBuilder) {
        return messageBuilder
                .setCookie(makeCookie(bfdCatch.getDiscriminator()))
                .setPriority(CATCH_RULES_PRIORITY)
                .setMatch(makeCatchRuleMatch(sw));
    }

    private Match makeCatchRuleMatch(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();

        return ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, switchManager.dpIdToMac(sw.getId()))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_DST, TransportPort.of(bfdCatch.getUdpPortNumber()))
                .build();
    }

    private static U64 makeCookie(int discriminator) {
        return U64.of(ISwitchManager.COOKIE_FLAG_SERVICE
                | ISwitchManager.COOKIE_FLAG_BFD_CATCH
                | discriminator);
    }

    // getter & setters
    protected NoviBfdCatch getBfdCatch() {
        return bfdCatch;
    }
}
