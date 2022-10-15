/* Copyright 2022 Telstra Open Source
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

package org.openkilda.floodlight.shared.packet;

import static java.lang.Short.toUnsignedInt;
import static java.lang.String.format;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.PacketParsingException;
import org.projectfloodlight.openflow.types.MacAddress;

import java.nio.ByteBuffer;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class Lacp extends BasePacket {
    public static byte LACP_VERSION = 0x01;
    public static byte ACTOR_TYPE = 0x01;
    public static byte PARTNER_TYPE = 0x02;
    public static byte TERMINATOR_PAD_COUNT = 52;
    public static byte LACP_LENGTH = 109;

    private ActorPartnerInfo actor;
    private ActorPartnerInfo partner;
    private CollectorInformation collectorInformation;

    @Override
    public byte[] serialize() {
        byte[] data = new byte[LACP_LENGTH];

        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(LACP_VERSION);
        bb.put(actor.serialize(ACTOR_TYPE));
        bb.put(partner.serialize(PARTNER_TYPE));
        bb.put(collectorInformation.serialize());
        for (int i = 0; i < TERMINATOR_PAD_COUNT; i++) {
            bb.put((byte) 0);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        if (bb.remaining() < LACP_LENGTH) {
            throw new PacketParsingException(format("Data %s is too short for LACP. "
                    + "Length of it must be at least %s", bytesToHex(data, offset, length), LACP_LENGTH));
        }
        byte version = bb.get();
        if (version != LACP_VERSION) {
            throw new PacketParsingException(format("Unknown LACP version %02X. Expected LACP version %02X",
                    version, LACP_VERSION));
        }

        byte[] actorBytes = new byte[ActorPartnerInfo.LENGTH];
        bb.get(actorBytes);
        actor = new ActorPartnerInfo();
        actor.deserialize(actorBytes);

        byte[] partnerBytes = new byte[ActorPartnerInfo.LENGTH];
        bb.get(partnerBytes);
        partner = new ActorPartnerInfo();
        partner.deserialize(partnerBytes);

        byte[] collectorBytes = new byte[CollectorInformation.LENGTH];
        bb.get(collectorBytes);
        collectorInformation = new CollectorInformation();
        collectorInformation.deserialize(collectorBytes);

        return this;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ActorPartnerInfo {
        public static final byte TLV_TYPE_ACTOR = 0x01;
        public static final byte TLV_TYPE_PARTNER = 0x02;
        public static final byte LENGTH = 20;
        private static final int RESERVED_LENGTH_BYTES = 3;

        private int systemPriority;
        private MacAddress systemId;
        private int key;
        private int portPriority;
        private int portNumber;
        private State state;

        public ActorPartnerInfo(ActorPartnerInfo info) {
            this(info.systemPriority, MacAddress.of(info.systemId.getLong()), info.key, info.portPriority,
                    info.portNumber, new State(info.state));
        }

        byte[] serialize(byte type) {
            byte[] data = new byte[LENGTH];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.put(type);
            bb.put(LENGTH);
            bb.putShort((short) systemPriority);
            bb.put(systemId.getBytes());
            bb.putShort((short) key);
            bb.putShort((short) portPriority);
            bb.putShort((short) portNumber);
            bb.put(state.serialize());
            for (int i = 0; i < RESERVED_LENGTH_BYTES; i++) {
                bb.put((byte) 0);
            }
            return data;
        }

        void deserialize(byte[] data) throws PacketParsingException {
            if (LENGTH > data.length) {
                throw new PacketParsingException(format("Data %s is too short for ActorPartnerInfo. "
                        + "Length of it must be at least %s", bytesToHex(data, 0, data.length), LENGTH));
            }
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.get(); // Actor or Partner type
            byte length = bb.get();
            if (length != LENGTH) {
                throw new PacketParsingException(format("Invalid length %s for ActorPartnerInfo. It must be %s",
                        length, LENGTH));
            }
            systemPriority = toUnsignedInt(bb.getShort());

            byte[] macAsBytes = new byte[MacAddress.FULL_MASK.getLength()];
            bb.get(macAsBytes);
            systemId = MacAddress.of(macAsBytes);
            key = toUnsignedInt(bb.getShort());
            portPriority = toUnsignedInt(bb.getShort());
            portNumber = toUnsignedInt(bb.getShort());

            state = new State();
            state.deserialize(bb.get());
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class State {
        private static final int ACTIVE_SHIFT = 0;
        private static final int SHORT_TIME_SHIFT = 1;
        private static final int AGGREGATABLE_SHIFT = 2;
        private static final int SYNCHRONISED_SHIFT = 3;
        private static final int COLLECTING_SHIFT = 4;
        private static final int DISTRIBUTING_SHIFT = 5;
        private static final int DEFAULTED_SHIFT = 6;
        private static final int EXPIRED_SHIFT = 7;

        private boolean active;
        private boolean shortTimeout;
        private boolean aggregatable;
        private boolean synchronised;
        private boolean collecting;
        private boolean distributing;
        private boolean defaulted;
        private boolean expired;

        public State(State state) {
            this(state.active, state.shortTimeout, state.aggregatable, state.synchronised, state.collecting,
                    state.distributing, state.defaulted, state.expired);
        }

        byte serialize() {
            byte data = 0;
            data = setField(data, active, ACTIVE_SHIFT);
            data = setField(data, shortTimeout, SHORT_TIME_SHIFT);
            data = setField(data, aggregatable, AGGREGATABLE_SHIFT);
            data = setField(data, synchronised, SYNCHRONISED_SHIFT);
            data = setField(data, collecting, COLLECTING_SHIFT);
            data = setField(data, distributing, DISTRIBUTING_SHIFT);
            data = setField(data, defaulted, DEFAULTED_SHIFT);
            data = setField(data, expired, EXPIRED_SHIFT);
            return data;
        }

        void deserialize(byte data) {
            active = getField(data, ACTIVE_SHIFT);
            shortTimeout = getField(data, SHORT_TIME_SHIFT);
            aggregatable = getField(data, AGGREGATABLE_SHIFT);
            synchronised = getField(data, SYNCHRONISED_SHIFT);
            collecting = getField(data, COLLECTING_SHIFT);
            distributing = getField(data, DISTRIBUTING_SHIFT);
            defaulted = getField(data, DEFAULTED_SHIFT);
            expired = getField(data, EXPIRED_SHIFT);
        }

        private byte setField(byte data, boolean value, int shift) {
            if (value) {
                data |= 1 << shift;
            }
            return data;
        }

        private boolean getField(byte data, int shift) {
            return (data & (1 << shift)) != 0;
        }
    }

    @Data
    public static class CollectorInformation {
        public static final byte TLV_TYPE_COLLECTOR_INFO = 0x03;

        public static final byte LENGTH = 16;
        private static final int RESERVED_LENGTH_BYTES = 12;

        private byte type;
        private int maxDelay;

        byte[] serialize() {
            byte[] data = new byte[LENGTH];
            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.put(type);
            bb.put(LENGTH);
            bb.putShort((short) maxDelay);
            for (int i = 0; i < RESERVED_LENGTH_BYTES; i++) {
                bb.put((byte) 0);
            }
            return data;
        }

        void deserialize(byte[] data) throws PacketParsingException {
            if (LENGTH > data.length) {
                throw new PacketParsingException(format("Data %s is too short for CollectorInformation. "
                        + "Length of it must be at least %s", bytesToHex(data, 0, LENGTH), LENGTH));
            }
            ByteBuffer bb = ByteBuffer.wrap(data, 0, LENGTH);
            type = bb.get();
            byte length = bb.get();
            if (length != LENGTH) {
                throw new PacketParsingException(format("Invalid length %s for ActorPartnerInfo. It must be %s",
                        length, LENGTH));
            }
            maxDelay = toUnsignedInt(bb.getShort());
        }
    }

    private static String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length && i < bytes.length; i++) {
            if (i > offset) {
                sb.append(' ');
            }
            sb.append(format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
