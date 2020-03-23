/* Copyright 2020 Telstra Open Source
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

package org.openkilda.grpc.speaker.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.grpc.speaker.model.PacketInOutStatsResponse;

import io.grpc.noviflow.PacketInOutStats;
import io.grpc.noviflow.YesNo;
import org.junit.Assert;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

public class NoviflowResponseMapperTest {

    public static final long PACKET_IN_TOTAL_PACKETS = 1L;
    public static final long PACKET_IN_TOTAL_PACKETS_DATAPLANE = 2L;
    public static final long PACKET_IN_NO_MATCH_PACKETS = 3L;
    public static final long PACKET_IN_APPLY_ACTION_PACKETS = 4L;
    public static final long PACKET_IN_INVALID_TTL_PACKETS = 5L;
    public static final long PACKET_IN_ACTION_SET_PACKETS = 6L;
    public static final long PACKET_IN_GROUP_PACKETS = 7L;
    public static final long PACKET_IN_PACKET_OUT_PACKETS = 8L;
    public static final long PACKET_OUT_TOTAL_PACKETS_DATAPLANE = 9L;
    public static final long PACKET_OUT_TOTAL_PACKETS_HOST = 10L;
    public static final int REPLY_STATUS = 11;
    private NoviflowResponseMapper mapper = Mappers.getMapper(NoviflowResponseMapper.class);

    @Test
    public void yesNoTest() {
        Assert.assertTrue(mapper.toBoolean(YesNo.YES));
        Assert.assertFalse(mapper.toBoolean(YesNo.NO));
        assertNull(mapper.toBoolean(null));
        assertNull(mapper.toBoolean(YesNo.UNRECOGNIZED));
        assertNull(mapper.toBoolean(YesNo.YESNO_RESERVED));
    }

    @Test
    public void packetInOutTest() {
        PacketInOutStats stats = PacketInOutStats.newBuilder()
                .setPacketInTotalPackets(PACKET_IN_TOTAL_PACKETS)
                .setPacketInTotalPacketsDataplane(PACKET_IN_TOTAL_PACKETS_DATAPLANE)
                .setPacketInNoMatchPackets(PACKET_IN_NO_MATCH_PACKETS)
                .setPacketInApplyActionPackets(PACKET_IN_APPLY_ACTION_PACKETS)
                .setPacketInInvalidTtlPackets(PACKET_IN_INVALID_TTL_PACKETS)
                .setPacketInActionSetPackets(PACKET_IN_ACTION_SET_PACKETS)
                .setPacketInGroupPackets(PACKET_IN_GROUP_PACKETS)
                .setPacketInPacketOutPackets(PACKET_IN_PACKET_OUT_PACKETS)
                .setPacketOutTotalPacketsDataplane(PACKET_OUT_TOTAL_PACKETS_DATAPLANE)
                .setPacketOutTotalPacketsHost(PACKET_OUT_TOTAL_PACKETS_HOST)
                .setPacketOutEth0InterfaceUp(YesNo.YES)
                .setReplyStatus(REPLY_STATUS)
                .build();
        PacketInOutStatsResponse response = mapper.toPacketInOutStatsResponse(stats);
        assertEquals(PACKET_IN_TOTAL_PACKETS, response.getPacketInTotalPackets());
        assertEquals(PACKET_IN_TOTAL_PACKETS_DATAPLANE, response.getPacketInTotalPacketsDataplane());
        assertEquals(PACKET_IN_NO_MATCH_PACKETS, response.getPacketInNoMatchPackets());
        assertEquals(PACKET_IN_APPLY_ACTION_PACKETS, response.getPacketInApplyActionPackets());
        assertEquals(PACKET_IN_INVALID_TTL_PACKETS, response.getPacketInInvalidTtlPackets());
        assertEquals(PACKET_IN_ACTION_SET_PACKETS, response.getPacketInActionSetPackets());
        assertEquals(PACKET_IN_GROUP_PACKETS, response.getPacketInGroupPackets());
        assertEquals(PACKET_IN_PACKET_OUT_PACKETS, response.getPacketInPacketOutPackets());
        assertEquals(PACKET_OUT_TOTAL_PACKETS_DATAPLANE, response.getPacketOutTotalPacketsDataplane());
        assertEquals(PACKET_OUT_TOTAL_PACKETS_HOST, response.getPacketOutTotalPacketsHost());
        assertEquals(Boolean.TRUE, response.getPacketOutEth0InterfaceUp());
        assertEquals(REPLY_STATUS, response.getReplyStatus());
    }
}
