/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.model.flow;

import org.openkilda.floodlight.exc.CorruptedNetworkDataException;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.flow.VerificationMeasures;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.UUID;

public class VerificationData {
    private static String JWT_KEY_PREFIX = "flow.verification.";

    private long sendTime = 0;
    private long recvTime = 0;
    private long senderLatency = 0;

    private final DatapathId source;
    private final DatapathId dest;
    private final UUID packetId;

    public static VerificationData of(DecodedJWT token) throws CorruptedNetworkDataException {
        long recvTime = System.currentTimeMillis();

        VerificationData data;
        try {
            DatapathId source = DatapathId.of(token.getClaim(makeJwtKey("source")).asLong());
            DatapathId dest = DatapathId.of(token.getClaim(makeJwtKey("dest")).asLong());
            UUID packetId = UUID.fromString(token.getClaim(makeJwtKey("id")).asString());

            data = new VerificationData(source, dest, packetId);
            data.setSenderLatency(token.getClaim(makeJwtKey("senderLatency")).asLong());
            data.setSendTime(token.getClaim(makeJwtKey("time")).asLong());
            data.setRecvTime(recvTime);
        } catch (NullPointerException e) {
            throw new CorruptedNetworkDataException(
                    String.format("Corrupted flow verification package (%s)", token));
        }

        return data;
    }

    public static VerificationData of(UniFlowVerificationRequest verificationRequest) {
        DatapathId source = DatapathId.of(verificationRequest.getSourceSwitchId());
        DatapathId dest = DatapathId.of(verificationRequest.getDestSwitchId());
        return new VerificationData(source, dest, verificationRequest.getPacketId());
    }

    public VerificationData(DatapathId source, DatapathId dest, UUID packetId) {
        this.source = source;
        this.dest = dest;
        this.packetId = packetId;
    }

    public JWTCreator.Builder toJWT(JWTCreator.Builder token) {
        token.withClaim(makeJwtKey("source"), source.getLong());
        token.withClaim(makeJwtKey("dest"), dest.getLong());
        token.withClaim(makeJwtKey("id"), packetId.toString());

        token.withClaim(makeJwtKey("senderLatency"), getSenderLatency());
        sendTime = System.currentTimeMillis();
        token.withClaim(makeJwtKey("time"), sendTime);

        return token;
    }

    public VerificationMeasures produceMeasurements(long recipientLatency) {
        long latency = getRecvTime() - getSendTime() - getSenderLatency() - recipientLatency;
        if (latency < 0) {
            latency = -1;
        }
        return new VerificationMeasures(latency, getSenderLatency(), recipientLatency);
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

    public DatapathId getSource() {
        return source;
    }

    public DatapathId getDest() {
        return dest;
    }

    public UUID getPacketId() {
        return packetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        VerificationData that = (VerificationData) o;

        return new EqualsBuilder()
                .append(source, that.source)
                .append(dest, that.dest)
                .append(packetId, that.packetId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(source)
                .append(dest)
                .append(packetId)
                .toHashCode();
    }

    private static String makeJwtKey(String name) {
        return JWT_KEY_PREFIX + name;
    }
}
