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

import static java.lang.String.format;

import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowApplyActions.FlowApplyActionsBuilder;
import org.openkilda.messaging.info.rule.FlowCopyFieldAction;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowInstructions.FlowInstructionsBuilder;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowMatchField.FlowMatchFieldBuilder;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.FlowSwapFieldAction;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.Set;

@Mapper
public class FlowEntryConverter {
    public static final FlowEntryConverter INSTANCE = Mappers.getMapper(FlowEntryConverter.class);

    /**
     * Converts flow representation.
     */
    public FlowEntry toFlowEntry(FlowSpeakerData flowSpeakerData) {
        return FlowEntry.builder()
                .cookie(flowSpeakerData.getCookie().getValue())
                .durationSeconds(flowSpeakerData.getDurationSeconds())
                .durationNanoSeconds(flowSpeakerData.getDurationNanoSeconds())
                .tableId(flowSpeakerData.getTable().getTableId())
                .packetCount(flowSpeakerData.getPacketCount())
                .version(flowSpeakerData.getOfVersion().toString())
                .priority(flowSpeakerData.getPriority())
                .idleTimeout(flowSpeakerData.getIdleTimeout())
                .hardTimeout(flowSpeakerData.getHardTimeout())
                .byteCount(flowSpeakerData.getByteCount())
                .match(convertMatch(flowSpeakerData.getMatch()))
                .instructions(convertInstructions(flowSpeakerData.getInstructions()))
                .flags(convertFlags(flowSpeakerData.getFlags()))
                .build();
    }

    private FlowMatchField convertMatch(Set<FieldMatch> match) {
        FlowMatchFieldBuilder builder = FlowMatchField.builder();
        match.forEach(m -> addMatch(builder, m));
        return builder.build();
    }

    private void addMatch(FlowMatchFieldBuilder builder, FieldMatch fieldMatch) {
        switch (fieldMatch.getField()) {
            case ETH_TYPE:
                builder.ethType(String.valueOf(fieldMatch.getValue()));
                return;
            case ETH_SRC:
                builder.ethSrc(String.valueOf(fieldMatch.getValue()));
                return;
            case ETH_DST:
                builder.ethDst(String.valueOf(fieldMatch.getValue()));
                return;
            case IN_PORT:
                builder.inPort(String.valueOf(fieldMatch.getValue()));
                return;
            case IP_PROTO:
                builder.ipProto(String.valueOf(fieldMatch.getValue()));
                return;
            case UDP_SRC:
                builder.udpSrc(String.valueOf(fieldMatch.getValue()));
                return;
            case UDP_DST:
                builder.udpDst(String.valueOf(fieldMatch.getValue()));
                return;
            case METADATA:
                builder.metadataValue(String.valueOf(fieldMatch.getValue()));
                builder.metadataMask(String.valueOf(fieldMatch.getMask()));
                return;
            case VLAN_VID:
                builder.vlanVid(String.valueOf(fieldMatch.getValue()));
                return;
            case OVS_VXLAN_VNI:
            case NOVIFLOW_TUNNEL_ID:
                builder.tunnelId(String.valueOf(fieldMatch.getValue()));
                return;
            default:
                throw new IllegalStateException(format("Unknown field match %s", fieldMatch.getField()));
        }
    }

    private FlowInstructions convertInstructions(Instructions instructions) {
        FlowInstructionsBuilder builder = FlowInstructions.builder();
        if (instructions.getGoToTable() != null) {
            builder.goToTable((short) instructions.getGoToTable().getTableId());
        }
        if (instructions.getGoToMeter() != null) {
            builder.goToMeter(instructions.getGoToMeter().getValue());
        }
        FlowApplyActionsBuilder actionsBuilder = FlowApplyActions.builder();
        if (instructions.getApplyActions() != null) {
            instructions.getApplyActions().forEach(action -> addAction(actionsBuilder, action));
        }
        return builder.build();
    }

    private void addAction(FlowApplyActionsBuilder builder, Action action) {
        switch (action.getType()) {
            case GROUP:
                GroupAction groupAction = (GroupAction) action;
                builder.group(String.valueOf(groupAction.getGroupId().getValue()));
                return;
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                builder.flowOutput(portOutAction.getPortNumber().toString());
                return;
            case POP_VLAN:
                builder.popVlan("");
                return;
            case PUSH_VLAN:
                builder.pushVlan("");
                return;
            case POP_VXLAN_NOVIFLOW:
            case POP_VXLAN_OVS:
                // todo handle pop vxlan
                return;
            case PUSH_VXLAN_NOVIFLOW:
            case PUSH_VXLAN_OVS:
                PushVxlanAction pushVxlanAction = (PushVxlanAction) action;
                builder.pushVxlan(String.valueOf(pushVxlanAction.getVni()));
                return;
            case METER:
                MeterAction meterAction = (MeterAction) action;
                builder.meter(String.valueOf(meterAction.getMeterId().getValue()));
                return;
            case NOVI_COPY_FIELD:
                CopyFieldAction copyFieldAction = (CopyFieldAction) action;
                builder.copyFieldAction(FlowCopyFieldAction.builder()
                        .bits(String.valueOf(copyFieldAction.getNumberOfBits()))
                        .srcOffset(String.valueOf(copyFieldAction.getSrcOffset()))
                        .dstOffset(String.valueOf(copyFieldAction.getDstOffset()))
                        .srcOxm(copyFieldAction.getOxmSrcHeader().toString())
                        .dstOxm(copyFieldAction.getOxmDstHeader().toString())
                        .build());
                return;
            case NOVI_SWAP_FIELD:
            case KILDA_SWAP_FIELD:
                SwapFieldAction swapFieldAction = (SwapFieldAction) action;
                builder.swapFieldAction(FlowSwapFieldAction.builder()
                        .bits(String.valueOf(swapFieldAction.getNumberOfBits()))
                        .srcOffset(String.valueOf(swapFieldAction.getSrcOffset()))
                        .dstOffset(String.valueOf(swapFieldAction.getDstOffset()))
                        .srcOxm(swapFieldAction.getOxmSrcHeader().toString())
                        .dstOxm(swapFieldAction.getOxmDstHeader().toString())
                        .build());
                return;
            case SET_FIELD:
                SetFieldAction setFieldAction = (SetFieldAction) action;
                builder.setFieldAction(FlowSetFieldAction.builder()
                        .fieldName(setFieldAction.getField().toString())
                        .fieldValue(String.valueOf(setFieldAction.getValue()))
                        .build());
                return;
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    private String[] convertFlags(Set<OfFlowFlag> flags) {
        return flags.stream().map(OfFlowFlag::toString).toArray(String[]::new);
    }
}
