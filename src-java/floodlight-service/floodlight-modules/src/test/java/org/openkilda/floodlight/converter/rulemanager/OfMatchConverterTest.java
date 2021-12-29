/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.converter.rulemanager;

import static org.junit.Assert.assertEquals;

import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;

import java.util.HashSet;
import java.util.Set;

public class OfMatchConverterTest {

    @Test
    public void testConvertMatchMasked() {
        OFFactoryVer13 factory = new OFFactoryVer13();
        Set<FieldMatch> matchSet = new HashSet<>();
        matchSet.add(FieldMatch.builder().field(Field.ETH_TYPE).value(0x0800L).mask(0x0800L).build());
        matchSet.add(FieldMatch.builder().field(Field.ETH_SRC).value(1).mask(1L).build());
        matchSet.add(FieldMatch.builder().field(Field.ETH_DST).value(2).mask(2L).build());
        matchSet.add(FieldMatch.builder().field(Field.IP_PROTO).value(17).mask(17L).build());
        matchSet.add(FieldMatch.builder().field(Field.UDP_SRC).value(11).mask(11L).build());
        matchSet.add(FieldMatch.builder().field(Field.UDP_DST).value(22).mask(22L).build());
        matchSet.add(FieldMatch.builder().field(Field.IN_PORT).value(333).mask(333L).build());
        matchSet.add(FieldMatch.builder().field(Field.METADATA).value(44).mask(44L).build());
        matchSet.add(FieldMatch.builder().field(Field.VLAN_VID).value(3000).mask(3000L).build());
        matchSet.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(321).mask(321L).build());

        Match match = OfMatchConverter.INSTANCE.convertMatch(matchSet, factory);
        Masked<EthType> ethTypeMasked = match.getMasked(MatchField.ETH_TYPE);
        assertEquals(0x800, ethTypeMasked.getValue().getValue());
        assertEquals(0x800, ethTypeMasked.getMask().getValue());

        Masked<MacAddress> ethSrcMasked = match.getMasked(MatchField.ETH_SRC);
        assertEquals(MacAddress.of(1), ethSrcMasked.getValue());
        assertEquals(MacAddress.of(1), ethSrcMasked.getMask());

        Masked<MacAddress> ethDstMasked = match.getMasked(MatchField.ETH_DST);
        assertEquals(MacAddress.of(2), ethDstMasked.getValue());
        assertEquals(MacAddress.of(2), ethDstMasked.getMask());

        Masked<IpProtocol> ipProtoMasked = match.getMasked(MatchField.IP_PROTO);
        assertEquals(IpProtocol.of((short) 17), ipProtoMasked.getValue());
        assertEquals(IpProtocol.of((short) 17), ipProtoMasked.getMask());

        Masked<TransportPort> udpSrcMasked = match.getMasked(MatchField.UDP_SRC);
        assertEquals(TransportPort.of(11), udpSrcMasked.getValue());
        assertEquals(TransportPort.of(11), udpSrcMasked.getMask());

        Masked<TransportPort> udpDstMasked = match.getMasked(MatchField.UDP_DST);
        assertEquals(TransportPort.of(22), udpDstMasked.getValue());
        assertEquals(TransportPort.of(22), udpDstMasked.getMask());


        Masked<OFPort> inPortMasked = match.getMasked(MatchField.IN_PORT);
        assertEquals(OFPort.of(333), inPortMasked.getValue());
        assertEquals(OFPort.of(333), inPortMasked.getMask());

        Masked<OFMetadata> metadataMasked = match.getMasked(MatchField.METADATA);
        assertEquals(OFMetadata.of(U64.of(44)), metadataMasked.getValue());
        assertEquals(OFMetadata.of(U64.of(44)), metadataMasked.getMask());

        Masked<OFVlanVidMatch> vlanVidMasked = match.getMasked(MatchField.VLAN_VID);
        assertEquals(OFVlanVidMatch.ofVlan(3000), vlanVidMasked.getValue());
        assertEquals(OFVlanVidMatch.ofVlan(3000), vlanVidMasked.getMask());

        Masked<U64> tunnelIdMasked = match.getMasked(MatchField.TUNNEL_ID);
        assertEquals(U64.of(321), tunnelIdMasked.getValue());
        assertEquals(U64.of(321), tunnelIdMasked.getMask());
    }

    @Test
    public void testConvertMatchExact() {
        OFFactoryVer13 factory = new OFFactoryVer13();
        Set<FieldMatch> matchSet = new HashSet<>();
        matchSet.add(FieldMatch.builder().field(Field.ETH_TYPE).value(0x0800L).build());
        matchSet.add(FieldMatch.builder().field(Field.ETH_SRC).value(1).build());
        matchSet.add(FieldMatch.builder().field(Field.ETH_DST).value(2).build());
        matchSet.add(FieldMatch.builder().field(Field.IP_PROTO).value(17).build());
        matchSet.add(FieldMatch.builder().field(Field.UDP_SRC).value(11).build());
        matchSet.add(FieldMatch.builder().field(Field.UDP_DST).value(22).build());
        matchSet.add(FieldMatch.builder().field(Field.IN_PORT).value(333).build());
        matchSet.add(FieldMatch.builder().field(Field.METADATA).value(44).build());
        matchSet.add(FieldMatch.builder().field(Field.VLAN_VID).value(3000).build());
        matchSet.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(321).build());
        matchSet.add(FieldMatch.builder().field(Field.OVS_VXLAN_VNI).value(123).build());

        Match match = OfMatchConverter.INSTANCE.convertMatch(matchSet, factory);
        EthType ethType = match.get(MatchField.ETH_TYPE);
        assertEquals(0x800, ethType.getValue());

        MacAddress ethSrc = match.get(MatchField.ETH_SRC);
        assertEquals(MacAddress.of(1), ethSrc);

        MacAddress ethDst = match.get(MatchField.ETH_DST);
        assertEquals(MacAddress.of(2), ethDst);

        IpProtocol ipProto = match.get(MatchField.IP_PROTO);
        assertEquals(IpProtocol.of((short) 17), ipProto);

        TransportPort udpSrc = match.get(MatchField.UDP_SRC);
        assertEquals(TransportPort.of(11), udpSrc);

        TransportPort udpDst = match.get(MatchField.UDP_DST);
        assertEquals(TransportPort.of(22), udpDst);


        OFPort inPort = match.get(MatchField.IN_PORT);
        assertEquals(OFPort.of(333), inPort);

        OFMetadata metadata = match.get(MatchField.METADATA);
        assertEquals(OFMetadata.of(U64.of(44)), metadata);

        OFVlanVidMatch vlanVidMasked = match.get(MatchField.VLAN_VID);
        assertEquals(OFVlanVidMatch.ofVlan(3000), vlanVidMasked);

        U64 tunnelId = match.get(MatchField.TUNNEL_ID);
        assertEquals(U64.of(321), tunnelId);

        U32 vxlanVni = match.get(MatchField.KILDA_VXLAN_VNI);
        assertEquals(U32.of(123), vxlanVni);
    }

    @Test
    public void testConvertToRuleManagerMatchMasked() {
        OFFactoryVer13 factory = new OFFactoryVer13();
        Match match = factory.buildMatch()
                .setMasked(MatchField.ETH_TYPE, EthType.IPv4, EthType.IPv4)
                .setMasked(MatchField.ETH_SRC, MacAddress.of(1),  MacAddress.of(1))
                .setMasked(MatchField.ETH_DST, MacAddress.of(2), MacAddress.of(2))
                .setMasked(MatchField.IP_PROTO, IpProtocol.of((short) 17), IpProtocol.of((short) 17))
                .setMasked(MatchField.UDP_SRC, TransportPort.of(11), TransportPort.of(11))
                .setMasked(MatchField.UDP_DST, TransportPort.of(22), TransportPort.of(22))
                .setMasked(MatchField.METADATA, OFMetadata.of(U64.of(333)), OFMetadata.of(U64.of(333)))
                .setMasked(MatchField.IN_PORT, OFPort.of(12), OFPort.of(12))
                .setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(444), OFVlanVidMatch.ofVlan(444))
                .setMasked(MatchField.TUNNEL_ID, U64.of(555), U64.of(555))
                .build();

        Set<FieldMatch> expectedMatches = new HashSet<>();
        expectedMatches.add(FieldMatch.builder().value(2048).mask(2048L).field(Field.ETH_TYPE).build());
        expectedMatches.add(FieldMatch.builder().value(1).mask(1L).field(Field.ETH_SRC).build());
        expectedMatches.add(FieldMatch.builder().value(2).mask(2L).field(Field.ETH_DST).build());
        expectedMatches.add(FieldMatch.builder().value(17).mask(17L).field(Field.IP_PROTO).build());
        expectedMatches.add(FieldMatch.builder().value(11).mask(11L).field(Field.UDP_SRC).build());
        expectedMatches.add(FieldMatch.builder().value(22).mask(22L).field(Field.UDP_DST).build());
        expectedMatches.add(FieldMatch.builder().value(333).mask(333L).field(Field.METADATA).build());
        expectedMatches.add(FieldMatch.builder().value(12).mask(12L).field(Field.IN_PORT).build());
        expectedMatches.add(FieldMatch.builder().value(444).mask(444L).field(Field.VLAN_VID).build());
        expectedMatches.add(FieldMatch.builder().value(555).mask(555L).field(Field.NOVIFLOW_TUNNEL_ID).build());

        Set<FieldMatch> fieldMatches = OfMatchConverter.INSTANCE.convertToRuleManagerMatch(match);
        assertEquals(expectedMatches, fieldMatches);
    }


    @Test
    public void testConvertToRuleManagerMatchExact() {
        OFFactoryVer13 factory = new OFFactoryVer13();
        Match match = factory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.ETH_SRC, MacAddress.of(1))
                .setExact(MatchField.ETH_DST, MacAddress.of(2))
                .setExact(MatchField.IP_PROTO, IpProtocol.of((short) 17))
                .setExact(MatchField.UDP_SRC, TransportPort.of(11))
                .setExact(MatchField.UDP_DST, TransportPort.of(22))
                .setExact(MatchField.METADATA, OFMetadata.of(U64.of(333)))
                .setExact(MatchField.IN_PORT, OFPort.of(12))
                .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(444))
                .setExact(MatchField.TUNNEL_ID, U64.of(555))
                .setExact(MatchField.KILDA_VXLAN_VNI, U32.of(666))
                .build();

        Set<FieldMatch> expectedMatches = new HashSet<>();
        expectedMatches.add(FieldMatch.builder().value(2048).field(Field.ETH_TYPE).build());
        expectedMatches.add(FieldMatch.builder().value(1).field(Field.ETH_SRC).build());
        expectedMatches.add(FieldMatch.builder().value(2).field(Field.ETH_DST).build());
        expectedMatches.add(FieldMatch.builder().value(17).field(Field.IP_PROTO).build());
        expectedMatches.add(FieldMatch.builder().value(11).field(Field.UDP_SRC).build());
        expectedMatches.add(FieldMatch.builder().value(22).field(Field.UDP_DST).build());
        expectedMatches.add(FieldMatch.builder().value(333).field(Field.METADATA).build());
        expectedMatches.add(FieldMatch.builder().value(12).field(Field.IN_PORT).build());
        expectedMatches.add(FieldMatch.builder().value(444).field(Field.VLAN_VID).build());
        expectedMatches.add(FieldMatch.builder().value(555).field(Field.NOVIFLOW_TUNNEL_ID).build());
        expectedMatches.add(FieldMatch.builder().value(666).field(Field.OVS_VXLAN_VNI).build());
        Set<FieldMatch> fieldMatches = OfMatchConverter.INSTANCE.convertToRuleManagerMatch(match);
        assertEquals(expectedMatches, fieldMatches);
    }

}
