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

package org.openkilda.floodlight.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsProp;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFactoryVer14;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.Collections;

public class OfPortStatsMapperTest {

    public static final long rxPackets = 1;
    public static final long txPackets = 2;
    public static final long rxBytes = 3;
    public static final long txBytes = 4;
    public static final long rxDropped = 5;
    public static final long txDropped = 6;
    public static final long rxErrors = 7;
    public static final long txErrors = 8;
    public static final long rxFrameErr = 9;
    public static final long rxOverErr = 10;
    public static final long rxCrcErr = 11;
    public static final long collisions = 12;
    public static final SwitchId switchId = new SwitchId(13);
    public static final int xId = 14;
    public static final OFPort port = OFPort.CONTROLLER;

    @Test
    public void testToPortStatsDataV13() {
        OFFactoryVer13 ofFactoryVer13 = new OFFactoryVer13();
        OFPortStatsEntry ofPortStatsEntry = prebuildPortStatsEntry(ofFactoryVer13.buildPortStatsEntry())
                .setRxFrameErr(U64.of(rxFrameErr))
                .setRxOverErr(U64.of(rxOverErr))
                .setRxCrcErr(U64.of(rxCrcErr))
                .setCollisions(U64.of(collisions)).build();

        OFPortStatsReply ofPortStatsReply = ofFactoryVer13.buildPortStatsReply()
                .setXid(xId)
                .setEntries(Collections.singletonList(ofPortStatsEntry))
                .build();

        PortStatsData data = OfPortStatsMapper.INSTANCE.toPostStatsData(
                Collections.singletonList(ofPortStatsReply), switchId);
        assertPortStatsData(data);
    }

    @Test
    public void testToPortStatsDataV14() {
        OFFactoryVer14 ofFactoryVer14 = new OFFactoryVer14();

        OFPortStatsProp opticalProps = ofFactoryVer14.buildPortStatsPropOptical().setRxPwr(123).build();
        OFPortStatsProp ethernetProps = ofFactoryVer14.buildPortStatsPropEthernet()
                .setRxFrameErr(U64.of(rxFrameErr))
                .setRxOverErr(U64.of(rxOverErr))
                .setRxCrcErr(U64.of(rxCrcErr))
                .setCollisions(U64.of(collisions))
                .build();

        OFPortStatsEntry ofPortStatsEntry = prebuildPortStatsEntry(ofFactoryVer14.buildPortStatsEntry())
                .setProperties(Lists.newArrayList(opticalProps, ethernetProps))
                .build();

        OFPortStatsReply ofPortStatsReply = ofFactoryVer14.buildPortStatsReply()
                .setXid(xId)
                .setEntries(Collections.singletonList(ofPortStatsEntry))
                .build();

        PortStatsData data = OfPortStatsMapper.INSTANCE.toPostStatsData(
                Collections.singletonList(ofPortStatsReply), switchId);
        assertPortStatsData(data);
    }

    private void assertPortStatsData(PortStatsData data) {
        assertEquals(switchId, data.getSwitchId());
        assertEquals(1, data.getStats().size());

        PortStatsEntry entry = data.getStats().get(0);
        assertEquals(port.getPortNumber(), entry.getPortNo());
        assertEquals(rxPackets, entry.getRxPackets());
        assertEquals(txPackets, entry.getTxPackets());
        assertEquals(rxBytes, entry.getRxBytes());
        assertEquals(txBytes, entry.getTxBytes());
        assertEquals(rxDropped, entry.getRxDropped());
        assertEquals(txDropped, entry.getTxDropped());
        assertEquals(rxErrors, entry.getRxErrors());
        assertEquals(txErrors, entry.getTxErrors());
        assertEquals(rxFrameErr, entry.getRxFrameErr());
        assertEquals(rxOverErr, entry.getRxOverErr());
        assertEquals(rxCrcErr, entry.getRxCrcErr());
        assertEquals(collisions, entry.getCollisions());
    }

    private OFPortStatsEntry.Builder prebuildPortStatsEntry(OFPortStatsEntry.Builder builder) {
        return builder.setPortNo(OFPort.CONTROLLER)
                      .setRxPackets(U64.of(rxPackets))
                      .setTxPackets(U64.of(txPackets))
                      .setRxBytes(U64.of(rxBytes))
                      .setTxBytes(U64.of(txBytes))
                      .setRxDropped(U64.of(rxDropped))
                      .setTxDropped(U64.of(txDropped))
                      .setRxErrors(U64.of(rxErrors))
                      .setTxErrors(U64.of(txErrors));
    }
}
