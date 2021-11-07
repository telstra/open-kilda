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

package org.openkilda.floodlight.converter;

import static java.lang.String.format;

import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public abstract class OfFlowModConverter {
    public static final OfFlowModConverter INSTANCE = Mappers.getMapper(OfFlowModConverter.class);

    /**
     * Convert flow speaker command data into OfFlowMod representation.
     */
    public OFFlowMod convertInstallCommand(FlowSpeakerCommandData commandData, OFFactory ofFactory) {
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(commandData.getCookie().getValue()))
                .setTableId(TableId.of(commandData.getTable().getTableId()))
                .setPriority(commandData.getPriority())
                .setMatch(convertMatch(commandData.getMatch(), ofFactory))
                .setInstructions(convertInstructions(commandData.getInstructions(), ofFactory))
                .build();
    }

    private Match convertMatch(Set<FieldMatch> match, OFFactory ofFactory) {
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

    private List<OFInstruction> convertInstructions(Instructions instructions, OFFactory ofFactory) {
        List<OFInstruction> result = new ArrayList<>();
        if (instructions.getGoToTable() != null) {
            result.add(ofFactory.instructions().gotoTable(TableId.of(instructions.getGoToTable().getTableId())));
        }
        if (instructions.getGoToMeter() != null) {
            result.add(ofFactory.instructions().buildMeter()
                    .setMeterId(instructions.getGoToMeter().getValue())
                    .build());
        }
        if (instructions.getWriteMetadata() != null) {
            result.add(ofFactory.instructions().buildWriteMetadata()
                    .setMetadata(U64.of(instructions.getWriteMetadata().getValue()))
                    .setMetadataMask(U64.of(instructions.getWriteMetadata().getMask()))
                    .build());
        }
        if (instructions.getApplyActions() != null) {
            result.add(convertApplyActions(instructions.getApplyActions(), ofFactory));
        }
        return result;
    }

    private OFInstruction convertApplyActions(List<Action> actions, OFFactory ofFactory) {
        List<OFAction> applyActions = actions.stream()
                .map(action -> convertAction(action, ofFactory))
                        .collect(Collectors.toList());

        return ofFactory.instructions().applyActions(applyActions);
    }

    private OFAction convertAction(Action action, OFFactory ofFactory) {
        switch (action.getType()) {
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                return ofFactory.actions().buildOutput()
                        .setPort(convertPort(portOutAction.getPortNumber()))
                        .build();
            // todo (rule-manager-fl-integration): add other port types
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    private OFPort convertPort(PortNumber portNumber) {
        if (portNumber.getPortNumber() != 0) {
            return OFPort.of(portNumber.getPortNumber());
        } else {
            switch (portNumber.getPortType()) {
                case LOCAL:
                    return OFPort.LOCAL;
                case CONTROLLER:
                    return OFPort.CONTROLLER;
                case IN_PORT:
                    return OFPort.IN_PORT;
                default:
                    throw new IllegalStateException(format("Unknown port type %s", portNumber.getPortType()));
            }
        }
    }
}
