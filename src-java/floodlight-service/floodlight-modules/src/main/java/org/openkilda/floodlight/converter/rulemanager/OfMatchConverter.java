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

import static java.lang.String.format;

import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.match.FieldMatch.FieldMatchBuilder;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFValueType;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;

import java.util.HashSet;
import java.util.Set;

@Mapper
@Slf4j
public class OfMatchConverter {
    public static final OfMatchConverter INSTANCE = Mappers.getMapper(OfMatchConverter.class);


    /**
     * Convert match.
     */
    public Set<FieldMatch> convertToRuleManagerMatch(Match match) {
        Set<FieldMatch> fieldMatches = new HashSet<>();
        for (MatchField field : match.getMatchFields()) {
            if (match.isExact(field)) {
                fieldMatches.add(getExact(match, field));
            } else {
                fieldMatches.add(getMasked(match, field));
            }
        }
        return fieldMatches;
    }

    private FieldMatch getExact(Match match, MatchField field) {
        OFValueType ofValueType = match.get(field);

        FieldMatchBuilder builder = FieldMatch.builder();
        switch (field.id) {
            case ETH_SRC:
                builder.field(Field.ETH_SRC);
                builder.value(match.get(MatchField.ETH_SRC).getLong());
                break;
            case ETH_DST:
                builder.field(Field.ETH_DST);
                builder.value(match.get(MatchField.ETH_DST).getLong());
                break;
            case ETH_TYPE:
                builder.field(Field.ETH_TYPE);
                builder.value(match.get(MatchField.ETH_TYPE).getValue());
                break;
            case IP_PROTO:
                builder.field(Field.IP_PROTO);
                builder.value(match.get(MatchField.IP_PROTO).getIpProtocolNumber());
                break;
            case UDP_SRC:
                builder.field(Field.UDP_SRC);
                builder.value(match.get(MatchField.UDP_SRC).getPort());
                break;
            case UDP_DST:
                builder.field(Field.UDP_DST);
                builder.value(match.get(MatchField.UDP_DST).getPort());
                break;
            case METADATA:
                builder.field(Field.METADATA);
                builder.value(match.get(MatchField.METADATA).getValue().getValue());
                break;
            case IN_PORT:
                builder.field(Field.IN_PORT);
                builder.value(match.get(MatchField.IN_PORT).getPortNumber());
                break;
            case VLAN_VID:
                builder.field(Field.VLAN_VID);
                builder.value(match.get(MatchField.VLAN_VID).getVlan());
                break;
            case TUNNEL_ID:
                builder.field(Field.NOVIFLOW_TUNNEL_ID);
                builder.value(match.get(MatchField.TUNNEL_ID).getValue());
                break;
            case KILDA_VXLAN_VNI:
                builder.field(Field.OVS_VXLAN_VNI);
                builder.value(match.get(MatchField.KILDA_VXLAN_VNI).getValue());
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unexpected match field id=%s, for class=%s of a match", field.id,
                                field.getClass().getName(), match));
        }
        return builder.build();
    }

    private FieldMatch getMasked(Match match, MatchField field) {
        FieldMatchBuilder builder = FieldMatch.builder();
        switch (field.id) {
            case ETH_SRC:
                Masked<MacAddress> ethSrcMasked = match.getMasked(MatchField.ETH_SRC);
                builder.field(Field.ETH_SRC);
                builder.value(ethSrcMasked.getValue().getLong());
                builder.mask(ethSrcMasked.getMask().getLong());
                break;
            case ETH_DST:
                Masked<MacAddress> ethDstMasked = match.getMasked(MatchField.ETH_DST);
                builder.field(Field.ETH_DST);
                builder.value(ethDstMasked.getValue().getLong());
                builder.mask(ethDstMasked.getMask().getLong());
                break;
            case ETH_TYPE:
                Masked<EthType> ethTypeMasked = match.getMasked(MatchField.ETH_TYPE);
                builder.field(Field.ETH_TYPE);
                builder.value(ethTypeMasked.getValue().getValue());
                builder.mask((long) ethTypeMasked.getMask().getValue());
                break;
            case IP_PROTO:
                Masked<IpProtocol> ipProtocolMasked = match.getMasked(MatchField.IP_PROTO);
                builder.field(Field.IP_PROTO);
                builder.value(ipProtocolMasked.getValue().getIpProtocolNumber());
                builder.mask((long) ipProtocolMasked.getMask().getIpProtocolNumber());
                break;
            case UDP_SRC:
                Masked<TransportPort> udpSrcMasked = match.getMasked(MatchField.UDP_SRC);
                builder.field(Field.UDP_SRC);
                builder.value(udpSrcMasked.getValue().getPort());
                builder.mask((long) udpSrcMasked.getMask().getPort());
                break;
            case UDP_DST:
                Masked<TransportPort> udpDstMasked = match.getMasked(MatchField.UDP_DST);
                builder.field(Field.UDP_DST);
                builder.value(udpDstMasked.getValue().getPort());
                builder.mask((long) udpDstMasked.getMask().getPort());
                break;
            case METADATA:
                Masked<OFMetadata> metadataMasked = match.getMasked(MatchField.METADATA);
                builder.field(Field.METADATA);
                builder.value(metadataMasked.getValue().getValue().getValue());
                builder.mask(metadataMasked.getMask().getValue().getValue());
                break;
            case IN_PORT:
                Masked<OFPort> inPortMasked = match.getMasked(MatchField.IN_PORT);
                builder.field(Field.IN_PORT);
                builder.value(inPortMasked.getValue().getPortNumber());
                builder.mask((long) inPortMasked.getMask().getPortNumber());
                break;
            case VLAN_VID:
                Masked<OFVlanVidMatch> vlanVidMasked = match.getMasked(MatchField.VLAN_VID);
                builder.field(Field.VLAN_VID);
                builder.value(vlanVidMasked.getValue().getVlan());
                builder.mask((long) vlanVidMasked.getMask().getVlan());
                break;
            case TUNNEL_ID:
                Masked<U64> tunnelIdMasked = match.getMasked(MatchField.TUNNEL_ID);
                builder.field(Field.NOVIFLOW_TUNNEL_ID);
                builder.value(tunnelIdMasked.getValue().getValue());
                builder.mask(tunnelIdMasked.getMask().getValue());
                break;
            case KILDA_VXLAN_VNI:
                Masked<U32> masked = match.getMasked(MatchField.KILDA_VXLAN_VNI);
                builder.field(Field.OVS_VXLAN_VNI);
                builder.value(masked.getValue().getValue());
                builder.mask(masked.getMask().getValue());
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unexpected match field id=%s, for class=%s of a match", field.id,
                                field.getClass().getName(), match));        }
        return builder.build();
    }

    /**
     * Convert match.
     */
    public Match convertMatch(Set<FieldMatch> match, OFFactory ofFactory) {
        Builder builder = ofFactory.buildMatch();
        match.forEach(fieldMatch -> processFieldMatch(builder, fieldMatch));
        return builder.build();
    }

    private void processFieldMatch(Builder builder, FieldMatch fieldMatch) {
        switch (fieldMatch.getField()) {
            case ETH_TYPE:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.ETH_TYPE, EthType.of((int) fieldMatch.getValue()),
                            EthType.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.ETH_TYPE, EthType.of((int) fieldMatch.getValue()));
                }
                break;
            case ETH_SRC:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.ETH_SRC, MacAddress.of((int) fieldMatch.getValue()),
                            MacAddress.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.ETH_SRC, MacAddress.of((int) fieldMatch.getValue()));
                }
                break;
            case ETH_DST:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.ETH_DST, MacAddress.of((int) fieldMatch.getValue()),
                            MacAddress.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.ETH_DST, MacAddress.of((int) fieldMatch.getValue()));
                }
                break;
            case IN_PORT:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.IN_PORT, OFPort.of((int) fieldMatch.getValue()),
                            OFPort.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.IN_PORT, OFPort.of((int) fieldMatch.getValue()));
                }
                break;
            case IP_PROTO:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.IP_PROTO, IpProtocol.of((short) fieldMatch.getValue()),
                            IpProtocol.of(fieldMatch.getMask().shortValue()));
                } else {
                    builder.setExact(MatchField.IP_PROTO, IpProtocol.of((short) fieldMatch.getValue()));
                }
                break;
            case UDP_SRC:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.UDP_SRC, TransportPort.of((int) fieldMatch.getValue()),
                            TransportPort.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.UDP_SRC, TransportPort.of((int) fieldMatch.getValue()));
                }
                break;
            case UDP_DST:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.UDP_DST, TransportPort.of((int) fieldMatch.getValue()),
                            TransportPort.of(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.UDP_DST, TransportPort.of((int) fieldMatch.getValue()));
                }
                break;
            case METADATA:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.METADATA, OFMetadata.of(U64.of(fieldMatch.getValue())),
                            OFMetadata.of(U64.of(fieldMatch.getMask())));
                } else {
                    builder.setExact(MatchField.METADATA, OFMetadata.of(U64.of(fieldMatch.getValue())));
                }
                break;
            case VLAN_VID:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan((int) fieldMatch.getValue()),
                            OFVlanVidMatch.ofVlan(fieldMatch.getMask().intValue()));
                } else {
                    builder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan((int) fieldMatch.getValue()));
                }
                break;
            case OVS_VXLAN_VNI:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.KILDA_VXLAN_VNI, U32.of(fieldMatch.getValue()),
                            U32.of(fieldMatch.getMask()));
                } else {
                    builder.setExact(MatchField.KILDA_VXLAN_VNI, U32.of(fieldMatch.getValue()));
                }
                break;
            case NOVIFLOW_TUNNEL_ID:
                if (fieldMatch.isMasked()) {
                    builder.setMasked(MatchField.TUNNEL_ID, U64.of(fieldMatch.getValue()),
                            U64.of(fieldMatch.getMask()));
                } else {
                    builder.setExact(MatchField.TUNNEL_ID, U64.of(fieldMatch.getValue()));
                }
                break;
            default:
                throw new IllegalStateException(format("Unknown field match %s", fieldMatch.getField()));
        }
    }
}
