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

package org.openkilda.wfm.topology.switchmanager.mappers;

import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.WriteMetadata;
import org.openkilda.messaging.info.switches.v2.action.BaseAction;
import org.openkilda.messaging.info.switches.v2.action.CopyFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.GroupActionEntry;
import org.openkilda.messaging.info.switches.v2.action.MeterActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PopVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PortOutActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PushVxlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SetFieldActionEntry;
import org.openkilda.messaging.info.switches.v2.action.SwapFieldActionEntry;
import org.openkilda.model.GroupId;
import org.openkilda.model.IPv4Address;
import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.match.FieldMatch;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public class RuleEntryConverter {
    public static final RuleEntryConverter INSTANCE = Mappers.getMapper(RuleEntryConverter.class);

    /**
     * Converts rule representation.
     */
    public RuleInfoEntryV2 toRuleEntry(FlowSpeakerData speakerData) {
        if (speakerData == null) {
            return null;
        }
        return RuleInfoEntryV2.builder()
                .cookie(speakerData.getCookie() != null ? speakerData.getCookie().getValue() : null)
                .priority(speakerData.getPriority())
                .tableId(Optional.ofNullable(speakerData.getTable())
                        .map(OfTable::getTableId)
                        .orElse(null))
                .cookieKind(null) // TODO(vshakirova): add cookie kind info
                .flags(Optional.ofNullable(speakerData.getFlags())
                        .map(f -> f.stream().map(OfFlowFlag::name)
                                .collect(Collectors.toList()))
                        .orElse(Collections.emptyList()))
                .instructions(convertInstructions(Optional.ofNullable(speakerData.getInstructions())
                        .orElse(Instructions.builder().build())))
                .match(convertMatch(Optional.ofNullable(speakerData.getMatch())
                        .orElse(Collections.emptySet())))
                .build();
    }

    private Map<String, RuleInfoEntryV2.FieldMatch> convertMatch(Set<FieldMatch> fieldMatches) {
        Map<String, RuleInfoEntryV2.FieldMatch> matches = new HashMap<>();

        for (FieldMatch fieldMatch : fieldMatches) {
            RuleInfoEntryV2.FieldMatch info = convertFieldMatch(fieldMatch);

            String fieldName = Optional.ofNullable(fieldMatch.getField())
                    .map(Field::name)
                    .orElse(null);
            matches.put(fieldName, info);
        }

        return matches;
    }

    private RuleInfoEntryV2.FieldMatch convertFieldMatch(FieldMatch fieldMatch) {
        Long mask = fieldMatch.getMask() == null || fieldMatch.getMask() == -1 ? null : fieldMatch.getMask();

        return RuleInfoEntryV2.FieldMatch.builder()
                .mask(mask)
                .value(Optional.of(fieldMatch.getValue())
                        .orElse(null))
                .build();
    }

    private RuleInfoEntryV2.Instructions convertInstructions(Instructions instructions) {
        return RuleInfoEntryV2.Instructions.builder()
                .goToTable(Optional.ofNullable(instructions.getGoToTable())
                        .map(OfTable::getTableId)
                        .orElse(null))
                .goToMeter(Optional.ofNullable(instructions.getGoToMeter())
                        .map(MeterId::getValue)
                        .orElse(null))
                .writeMetadata(convertWriteMetadata(instructions.getWriteMetadata()))
                .applyActions(Optional.ofNullable(instructions.getApplyActions())
                        .orElse(Collections.emptyList()).stream()
                        .map(this::convertActions)
                        .collect(Collectors.toList()))
                .writeActions(Optional.ofNullable(instructions.getWriteActions())
                        .orElse(Collections.emptySet()).stream()
                        .map(this::convertActions)
                        .collect(Collectors.toList()))
                .build();
    }

    private WriteMetadata convertWriteMetadata(OfMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return WriteMetadata.builder()
                .mask(metadata.getMask())
                .value(metadata.getValue())
                .build();
    }

    private BaseAction convertActions(Action action) {
        BaseAction result;
        switch (action.getType()) {
            case GROUP:
                GroupAction groupAction = (GroupAction) action;
                result = GroupActionEntry.builder()
                        .groupId(Optional.ofNullable(groupAction.getGroupId())
                                .map(GroupId::getValue)
                                .orElse(null))
                        .build();
                break;
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                result = PortOutActionEntry.builder()
                        .portNumber(Optional.ofNullable(portOutAction.getPortNumber())
                                .map(ProtoConstants.PortNumber::getPortNumber)
                                .orElse(null))
                        .portType(Optional.ofNullable(portOutAction.getPortNumber())
                                .map(ProtoConstants.PortNumber::getPortType)
                                .map(SpecialPortType::name).orElse(null))
                        .build();
                break;
            case POP_VLAN:
                result = PopVlanActionEntry.builder().build();
                break;
            case PUSH_VLAN:
                result = PushVlanActionEntry.builder().build();
                break;
            case POP_VXLAN_NOVIFLOW:
            case POP_VXLAN_OVS:
                result = PopVxlanActionEntry.builder()
                        .actionType(action.getType().name())
                        .build();
                break;
            case PUSH_VXLAN_NOVIFLOW:
            case PUSH_VXLAN_OVS:
                PushVxlanAction pushVxlanAction = (PushVxlanAction) action;
                result = PushVxlanActionEntry.builder()
                        .actionType(action.getType().name())
                        .dstIpv4Address(Optional.ofNullable(pushVxlanAction.getDstIpv4Address())
                                .map(IPv4Address::getAddress)
                                .orElse(null))
                        .srcIpv4Address(Optional.ofNullable(pushVxlanAction.getSrcIpv4Address())
                                .map(IPv4Address::getAddress)
                                .orElse(null))
                        .udpSrc(pushVxlanAction.getUdpSrc())
                        .vni(pushVxlanAction.getVni())
                        .build();
                break;
            case METER:
                MeterAction meterAction = (MeterAction) action;
                result = MeterActionEntry.builder()
                        .meterId(meterAction.getMeterId())
                        .build();
                break;
            case SET_FIELD:
                SetFieldAction setField = (SetFieldAction) action;
                result = SetFieldActionEntry.builder()
                        .field(Optional.ofNullable(setField.getField())
                                .map(Field::name)
                                .orElse(null))
                        .value(setField.getValue())
                        .build();
                break;
            case NOVI_SWAP_FIELD:
            case KILDA_SWAP_FIELD:
                SwapFieldAction noviSwapField = (SwapFieldAction) action;
                result = SwapFieldActionEntry.builder()
                        .dstHeader(Optional.ofNullable(noviSwapField.getOxmDstHeader())
                                .map(OpenFlowOxms::name)
                                .orElse(null))
                        .srcHeader(Optional.ofNullable(noviSwapField.getOxmSrcHeader())
                                .map(OpenFlowOxms::name)
                                .orElse(null))
                        .actionType(action.getType().name())
                        .dstOffset(noviSwapField.getDstOffset())
                        .srcOffset(noviSwapField.getSrcOffset())
                        .numberOfBits(noviSwapField.getNumberOfBits())
                        .build();
                break;
            case NOVI_COPY_FIELD:
                CopyFieldAction noviCopyField = (CopyFieldAction) action;
                result = CopyFieldActionEntry.builder()
                        .dstHeader(Optional.ofNullable(noviCopyField.getOxmDstHeader())
                                .map(OpenFlowOxms::name)
                                .orElse(null))
                        .srcHeader(Optional.ofNullable(noviCopyField.getOxmSrcHeader())
                                .map(OpenFlowOxms::name)
                                .orElse(null))
                        .dstOffset(noviCopyField.getDstOffset())
                        .srcOffset(noviCopyField.getSrcOffset())
                        .numberOfBits(noviCopyField.getNumberOfBits())
                        .build();
                break;
            default:
                throw new IllegalStateException("Unexpected action type: " + action.getType());
        }
        return result;
    }
}
