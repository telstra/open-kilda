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

import org.openkilda.floodlight.error.CorruptedNetworkDataException;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.PingMeters;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.UUID;

public class PingData implements ISignPayload {
    @Getter @Setter
    private long sendTime = 0;
    @Getter @Setter
    private long senderLatency = 0;

    @Getter
    private final int ingressPortNumber;
    @Getter
    private final DatapathId source;
    @Getter
    private final DatapathId dest;
    @Getter
    private final UUID pingId;

    /**
     * Build {@link PingData} from {@link DecodedJWT} token.
     */
    public static PingData of(DecodedJWT token) throws CorruptedNetworkDataException {
        PingData data;
        try {
            DatapathId ingress = DatapathId.of(token.getClaim(makeIngressDatapathRef()).asLong());
            int ingressPortNumber = token.getClaim(makeIngressPortRef()).asInt();
            DatapathId egress = DatapathId.of(token.getClaim(makeEgressDatapathRef()).asLong());
            UUID packetId = UUID.fromString(token.getClaim(makePingIdRef()).asString());

            data = new PingData(ingressPortNumber, ingress, egress, packetId);
            data.setSenderLatency(token.getClaim(makeIngressLatencyRef()).asLong());
            data.setSendTime(token.getClaim(makeTimestampRef()).asLong());
        } catch (NullPointerException e) {
            throw new CorruptedNetworkDataException(
                    String.format("Corrupted flow verification package (%s)", token));
        }

        return data;
    }

    /**
     * Build {@link PingData} from {@link Ping} instance.
     */
    public static PingData of(Ping ping) {
        NetworkEndpoint ingress = ping.getSource();
        DatapathId source = DatapathId.of(ingress.getDatapath().toLong());
        DatapathId dest = DatapathId.of(ping.getDest().getDatapath().toLong());
        return new PingData(ingress.getPortNumber(), source, dest, ping.getPingId());
    }

    public PingData(int ingressPortNumber, DatapathId source, DatapathId dest, UUID pingId) {
        this.ingressPortNumber = ingressPortNumber;
        this.source = source;
        this.dest = dest;
        this.pingId = pingId;
    }

    /**
     * Populate data into JWT builder.
     */
    public JWTCreator.Builder toSign(JWTCreator.Builder token) {
        token.withClaim(makeIngressDatapathRef(), source.getLong());
        token.withClaim(makeIngressPortRef(), ingressPortNumber);
        token.withClaim(makeEgressDatapathRef(), dest.getLong());
        token.withClaim(makePingIdRef(), pingId.toString());

        token.withClaim(makeIngressLatencyRef(), getSenderLatency());
        sendTime = System.currentTimeMillis();
        token.withClaim(makeTimestampRef(), sendTime);

        return token;
    }

    /**
     * Calculate flow's latency.
     */
    public PingMeters produceMeasurements(long receiveTime, long recipientLatency) {
        long latency = receiveTime - getSendTime() - getSenderLatency() - recipientLatency;
        if (latency < 0) {
            latency = -1;
        }
        return new PingMeters(latency, getSenderLatency(), recipientLatency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PingData that = (PingData) o;

        return new EqualsBuilder()
                .append(source, that.source)
                .append(dest, that.dest)
                .append(pingId, that.pingId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(source)
                .append(dest)
                .append(pingId)
                .toHashCode();
    }

    private static String makeIngressDatapathRef() {
        return makeJwtKey("ingress");
    }

    private static String makeIngressPortRef() {
        return makeJwtKey("ingressPortNumber");
    }

    private static String makeEgressDatapathRef() {
        return makeJwtKey("egress");
    }

    private static String makePingIdRef() {
        return makeJwtKey("id");
    }

    private static String makeIngressLatencyRef() {
        return makeJwtKey("ingressLatency");
    }

    private static String makeTimestampRef() {
        return makeJwtKey("timestamp");
    }

    private static String makeJwtKey(String name) {
        return "openkilda.ping." + name;
    }
}
