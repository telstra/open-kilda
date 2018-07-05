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
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.PingMeters;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.UUID;

public class PingData implements ISignPayload {
    private static String JWT_KEY_PREFIX = "openkilda.ping.";

    private long sendTime = 0;
    private long recvTime = 0;
    private long senderLatency = 0;

    private final Short sourceVlan;
    private final DatapathId source;
    private final DatapathId dest;
    private final UUID pingId;

    /**
     * Build {@link PingData} from {@link DecodedJWT} token.
     */
    public static PingData of(DecodedJWT token) throws CorruptedNetworkDataException {
        long recvTime = System.currentTimeMillis();

        PingData data;
        try {
            Integer sourceVlan = token.getClaim(makeJwtKey("sourceVlan")).asInt();
            DatapathId source = DatapathId.of(token.getClaim(makeJwtKey("source")).asLong());
            DatapathId dest = DatapathId.of(token.getClaim(makeJwtKey("dest")).asLong());
            UUID packetId = UUID.fromString(token.getClaim(makeJwtKey("id")).asString());

            Short vlanId = null;
            if (sourceVlan != null) {
                vlanId = sourceVlan.shortValue();
            }
            data = new PingData(vlanId, source, dest, packetId);
            data.setSenderLatency(token.getClaim(makeJwtKey("senderLatency")).asLong());
            data.setSendTime(token.getClaim(makeJwtKey("time")).asLong());
            data.setRecvTime(recvTime);
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
        DatapathId source = DatapathId.of(ping.getSource().getSwitchDpId());
        DatapathId dest = DatapathId.of(ping.getDest().getSwitchDpId());
        return new PingData(ping.getSourceVlanId(), source, dest, ping.getPingId());
    }

    public PingData(Short sourceVlan, DatapathId source, DatapathId dest, UUID pingId) {
        this.sourceVlan = sourceVlan;
        this.source = source;
        this.dest = dest;
        this.pingId = pingId;
    }

    /**
     * Populate data into JWT builder.
     */
    public JWTCreator.Builder toSign(JWTCreator.Builder token) {
        if (sourceVlan != null) {
            token.withClaim(makeJwtKey("sourceVlan"), Integer.valueOf(sourceVlan));
        }
        token.withClaim(makeJwtKey("source"), source.getLong());
        token.withClaim(makeJwtKey("dest"), dest.getLong());
        token.withClaim(makeJwtKey("id"), pingId.toString());

        token.withClaim(makeJwtKey("senderLatency"), getSenderLatency());
        sendTime = System.currentTimeMillis();
        token.withClaim(makeJwtKey("time"), sendTime);

        return token;
    }

    /**
     * Calculate flow's latency.
     */
    public PingMeters produceMeasurements(long recipientLatency) {
        long latency = getRecvTime() - getSendTime() - getSenderLatency() - recipientLatency;
        if (latency < 0) {
            latency = -1;
        }
        return new PingMeters(latency, getSenderLatency(), recipientLatency);
    }

    public long getSendTime() {
        return sendTime;
    }

    private void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public long getRecvTime() {
        return recvTime;
    }

    private void setRecvTime(long recvTime) {
        this.recvTime = recvTime;
    }

    public long getSenderLatency() {
        return senderLatency;
    }

    public void setSenderLatency(long senderLatency) {
        this.senderLatency = senderLatency;
    }

    public Short getSourceVlan() {
        return sourceVlan;
    }

    public DatapathId getSource() {
        return source;
    }

    public DatapathId getDest() {
        return dest;
    }

    public UUID getPingId() {
        return pingId;
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

    private static String makeJwtKey(String name) {
        return JWT_KEY_PREFIX + name;
    }
}
