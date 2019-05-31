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

package org.openkilda.floodlight.model;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;

public class OfInput {
    private final long receiveTime;

    private final DatapathId dpId;
    private final OFMessage message;

    private final Long latency;
    private final Ethernet packetInPayload;

    private static final U64 packetInReservedCookieValue = U64.of(-1);

    public OfInput(IOFSwitch sw, OFMessage message, FloodlightContext context) {
        receiveTime = System.currentTimeMillis();

        this.dpId = sw.getId();
        this.message = message;
        packetInPayload = extractPacketInPayload(message, context);
        latency = extractSwitchLatency(sw);
    }

    /**
     * Extract cookie value from OFPacketIn message.
     */
    public U64 packetInCookie() {
        if (getType() != OFType.PACKET_IN) {
            throw new IllegalStateException(String.format(
                    "%s.packetInCookie() is applicable only for %s (called for %s)",
                    getClass().getName(), OFType.PACKET_IN, getType()));
        }

        OFPacketIn packet = (OFPacketIn) message;
        final U64 cookie;
        try {
            cookie = packet.getCookie();
        } catch (UnsupportedOperationException e) {
            return null;
        }

        return cookie;
    }

    /**
     * Check current packet(PACKET_IN) cookie value is mismatched with expected value.
     */
    public boolean packetInCookieMismatch(U64 expected, Logger log) {
        boolean isMismatched = packetInCookieMismatchCheck(expected);
        if (isMismatched) {
            log.debug("{} - cookie mismatch (expected:{} != actual:{})", this, expected, packetInCookie());
        }
        return isMismatched;
    }

    public OFType getType() {
        return message.getType();
    }

    public long getReceiveTime() {
        return receiveTime;
    }

    public DatapathId getDpId() {
        return dpId;
    }

    public OFMessage getMessage() {
        return message;
    }

    public Long getLatency() {
        return latency;
    }

    public Ethernet getPacketInPayload() {
        return packetInPayload;
    }

    @Override
    public String toString() {
        return String.format("%s ===> %s.%s:%d", dpId, message.getType(), message.getVersion(), message.getXid());
    }

    private boolean packetInCookieMismatchCheck(U64 expected) {
        U64 actual = packetInCookie();

        if (actual == null) {
            return false;
        }
        if (packetInReservedCookieValue.equals(actual)) {
            return false;
        }
        if (U64.ZERO.equals(actual)) {
            return false;
        }

        return !actual.equals(expected);
    }

    private static Ethernet extractPacketInPayload(OFMessage message, FloodlightContext context) {
        if (message.getType() != OFType.PACKET_IN) {
            return null;
        }
        return IFloodlightProviderService.bcStore.get(context, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    }

    private static Long extractSwitchLatency(IOFSwitch sw) {
        U64 swLatency = sw.getLatency();
        Long result = null;
        if (swLatency != null) {
            result = swLatency.getValue();
        }
        return result;
    }
}
