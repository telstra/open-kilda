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

package org.openkilda.floodlight.model;

import org.openkilda.floodlight.error.CorruptedNetworkDataException;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.PingMeters;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public class PingData {
    private static int SERIALIZE_BYTES_LENGTH = 32;

    private long sendTime = 0;
    private long senderLatency = 0;

    private final UUID pingId;

    /**
     * Build {@link PingData} from bytes representation.
     */
    public static PingData of(byte[] bytes) throws CorruptedNetworkDataException {
        PingData data;
        try {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            UUID pingId = new UUID(bb.getLong(), bb.getLong());

            data = new PingData(pingId);

            data.setSendTime(bb.getLong());
            data.setSenderLatency(bb.getLong());

        } catch (Exception e) {
            throw new CorruptedNetworkDataException("Corrupted flow verification package");
        }

        return data;
    }

    /**
     * Build {@link PingData} from {@link Ping} instance.
     */
    public static PingData of(Ping ping) {
        return new PingData(ping.getPingId());
    }

    public PingData(UUID pingId) {
        this.pingId = pingId;
    }

    /**
     * Get binary representation of {@link PingData}.
     * @return the byte array.
     */
    public byte[] serialize() {
        ByteBuffer bb = ByteBuffer.wrap(new byte[SERIALIZE_BYTES_LENGTH]);

        bb.putLong(pingId.getMostSignificantBits());
        bb.putLong(pingId.getLeastSignificantBits());

        bb.putLong(sendTime);
        bb.putLong(senderLatency);

        return bb.array();
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

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public long getSenderLatency() {
        return senderLatency;
    }

    public void setSenderLatency(long senderLatency) {
        this.senderLatency = senderLatency;
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
        PingData pingData = (PingData) o;
        return pingId.equals(pingData.pingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pingId);
    }
}
