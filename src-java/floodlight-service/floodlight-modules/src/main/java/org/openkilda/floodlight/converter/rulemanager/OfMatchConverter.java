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
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.HashSet;
import java.util.Set;

@Mapper
public class OfMatchConverter {
    public static final OfMatchConverter INSTANCE = Mappers.getMapper(OfMatchConverter.class);


    /**
     * Convert match.
     */
    // TODO(tdurakov): reimplement with match.getMatchFields()
    public Set<FieldMatch> convertToRuleManagerMatch(Match match) {
        Set<FieldMatch> fieldMatches = new HashSet<>();
        // ETH_SRC
        Masked<MacAddress> ethSrcMasked = match.getMasked(MatchField.ETH_SRC);
        if (ethSrcMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.ETH_SRC)
                    .value(ethSrcMasked.getValue().getLong())
                    .mask(ethSrcMasked.getMask().getLong())
                    .build());
        } else {
            MacAddress ethSrc = match.get(MatchField.ETH_SRC);
            if (ethSrc != null) {
                fieldMatches.add(FieldMatch.builder().field(Field.ETH_SRC).value(ethSrc.getLong()).build());
            }
        }

        // ETH_DST
        Masked<MacAddress> ethDstMasked = match.getMasked(MatchField.ETH_DST);
        if (ethDstMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.ETH_DST)
                    .value(ethDstMasked.getValue().getLong()).mask(ethDstMasked.getMask().getLong())
                    .build());
        } else {
            MacAddress ethDst = match.get(MatchField.ETH_DST);
            if (ethDst != null) {
                fieldMatches.add(FieldMatch.builder().field(Field.ETH_DST).value(ethDst.getLong()).build());
            }
        }

        // ETH_TYPE
        Masked<EthType> ethTypeMasked = match.getMasked(MatchField.ETH_TYPE);
        if (ethTypeMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.ETH_TYPE)
                    .value(ethTypeMasked.getValue().getValue())
                    .mask((long) ethTypeMasked.getMask().getValue())
                    .build());
        } else {
            EthType ethType = match.get(MatchField.ETH_TYPE);
            if (ethType != null) {
                fieldMatches.add(FieldMatch.builder().field(Field.ETH_TYPE).value(ethType.getValue()).build());
            }
        }

        // IP_PROTO
        Masked<IpProtocol> ipProtocolMasked = match.getMasked(MatchField.IP_PROTO);
        if (ethTypeMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.IP_PROTO)
                    .value(ipProtocolMasked.getValue().getIpProtocolNumber())
                    .mask((long) ipProtocolMasked.getMask().getIpProtocolNumber())
                    .build());
        } else {
            IpProtocol ipProtocol = match.get(MatchField.IP_PROTO);
            if (ipProtocol != null) {
                fieldMatches.add(
                        FieldMatch.builder().field(Field.IP_PROTO).value(ipProtocol.getIpProtocolNumber()).build());
            }
        }

        // UDP_SRC
        Masked<TransportPort> udpSrcMasked = match.getMasked(MatchField.UDP_SRC);
        if (udpSrcMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.UDP_SRC)
                    .value(udpSrcMasked.getValue().getPort())
                    .mask((long) udpSrcMasked.getMask().getPort())
                    .build());
        } else {
            TransportPort udpSrc = match.get(MatchField.UDP_SRC);
            if (udpSrc != null) {
                fieldMatches.add(FieldMatch.builder().field(Field.UDP_SRC).value(udpSrc.getPort()).build());
            }
        }

        // UDP_DST
        Masked<TransportPort> udpDstMasked = match.getMasked(MatchField.UDP_DST);
        if (udpDstMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.UDP_DST)
                    .value(udpDstMasked.getValue().getPort())
                    .mask((long) udpDstMasked.getMask().getPort())
                    .build());
        } else {
            TransportPort udpDst = match.get(MatchField.UDP_DST);
            if (udpDst != null) {
                fieldMatches.add(FieldMatch.builder().field(Field.UDP_DST).value(udpDst.getPort()).build());
            }
        }

        // METADATA
        Masked<OFMetadata> metadataMasked = match.getMasked(MatchField.METADATA);
        if (metadataMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.METADATA)
                    .value(metadataMasked.getValue().getValue().getValue())
                    .mask((long) metadataMasked.getMask().getValue().getValue())
                    .build());
        } else {
            OFMetadata metadata = match.get(MatchField.METADATA);
            if (metadata != null) {
                fieldMatches.add(FieldMatch.builder()
                        .field(Field.METADATA)
                        .value(metadata.getValue().getValue())
                        .build());
            }
        }
        Masked<OFPort> inPortMasked = match.getMasked(MatchField.IN_PORT);
        if (inPortMasked != null) {
            fieldMatches.add(FieldMatch.builder()
                    .field(Field.IN_PORT)
                    .value(inPortMasked.getValue().getPortNumber())
                    .mask((long) inPortMasked.getMask().getPortNumber())
                    .build());
        } else {
            OFPort inPort = match.get(MatchField.IN_PORT);
            if (inPort != null) {
                fieldMatches.add(FieldMatch.builder()
                        .field(Field.IN_PORT)
                        .value(inPort.getPortNumber())
                        .build());
            }
        }
        return fieldMatches;
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
            //todo (rule-manager-fl-integration): add other fields
            default:
                throw new IllegalStateException(format("Unknown field match %s", fieldMatch.getField()));
        }
    }
}
