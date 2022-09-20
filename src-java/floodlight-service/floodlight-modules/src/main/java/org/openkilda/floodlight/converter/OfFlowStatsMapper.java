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

package org.openkilda.floodlight.converter;

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowApplyActions.FlowApplyActionsBuilder;
import org.openkilda.messaging.info.rule.FlowCopyFieldAction;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.FlowSwapFieldAction;
import org.openkilda.messaging.info.rule.GroupBucket;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionKildaPushVxlanField;
import org.projectfloodlight.openflow.protocol.action.OFActionKildaSwapField;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowCopyField;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowPushVxlanTunnel;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowSwapField;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.types.Masked;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class that converts OFlowStats from the switch to kilda known format for further processing.
 */
@Mapper
@Slf4j
public abstract class OfFlowStatsMapper {

    public static final OfFlowStatsMapper INSTANCE = Mappers.getMapper(OfFlowStatsMapper.class);

    /**
     * OF specification added 13 bit that defines existence of vlan tag.
     */
    private static final int VLAN_MASK = 0xFFF;

    /**
     * Convert {@link OFFlowStatsEntry} to format that kilda supports {@link FlowEntry}.
     * @param entry flow stats to be converted.
     * @return result of transformation {@link FlowEntry}.
     */
    public FlowEntry toFlowEntry(final OFFlowStatsEntry entry) {
        return FlowEntry.builder()
                .version(entry.getVersion().toString())
                .durationSeconds(entry.getDurationSec())
                .durationNanoSeconds(entry.getDurationNsec())
                .hardTimeout(entry.getHardTimeout())
                .idleTimeout(entry.getIdleTimeout())
                .priority(entry.getPriority())
                .byteCount(entry.getByteCount().getValue())
                .packetCount(entry.getPacketCount().getValue())
                .flags(entry.getFlags().stream()
                        .map(OFFlowModFlags::name)
                        .toArray(String[]::new))
                .cookie(entry.getCookie().getValue())
                .tableId(entry.getTableId().getValue())
                .match(toFlowMatchField(entry.getMatch()))
                .instructions(toFlowInstructions(entry.getInstructions()))
                .build();
    }

    /**
     * Convert {@link OFFlowMod} to format that kilda supports {@link FlowEntry}.
     * @param entry flow stats to be converted.
     * @return result of transformation {@link FlowEntry}.
     */
    public FlowEntry toFlowEntry(final OFFlowMod entry) {
        return FlowEntry.builder()
                .version(entry.getVersion().toString())
                .hardTimeout(entry.getHardTimeout())
                .idleTimeout(entry.getIdleTimeout())
                .priority(entry.getPriority())
                .flags(entry.getFlags().stream()
                        .map(OFFlowModFlags::name)
                        .toArray(String[]::new))
                .cookie(entry.getCookie().getValue())
                .tableId(entry.getTableId().getValue())
                .match(toFlowMatchField(entry.getMatch()))
                .instructions(toFlowInstructions(entry.getInstructions()))
                .build();
    }

    /**
     * Convert {@link Match} to {@link FlowMatchField}.
     * @param match match to be converted.
     * @return result of transformation {@link FlowMatchField}.
     */
    public FlowMatchField toFlowMatchField(final Match match) {
        return FlowMatchField.builder()
                .vlanVid(Optional.ofNullable(match.get(MatchField.VLAN_VID))
                        .map(value -> String.valueOf(value.getVlan() & VLAN_MASK))
                        .orElse(null))
                .ethType(Optional.ofNullable(match.get(MatchField.ETH_TYPE))
                        .map(Objects::toString).orElse(null))
                .ethSrc(Optional.ofNullable(match.get(MatchField.ETH_SRC))
                        .map(Objects::toString).orElse(null))
                .ethDst(Optional.ofNullable(match.get(MatchField.ETH_DST))
                        .map(Objects::toString).orElse(null))
                .inPort(Optional.ofNullable(match.get(MatchField.IN_PORT))
                        .map(Objects::toString).orElse(null))
                .ipProto(Optional.ofNullable(match.get(MatchField.IP_PROTO))
                        .map(Objects::toString).orElse(null))
                .udpDst(Optional.ofNullable(match.get(MatchField.UDP_DST))
                        .map(Objects::toString).orElse(null))
                .udpSrc(Optional.ofNullable(match.get(MatchField.UDP_SRC))
                        .map(Objects::toString).orElse(null))
                .tunnelId(getTunnelId(match))
                .metadataValue(Optional.ofNullable(match.getMasked(MatchField.METADATA))
                        .map(Masked::getValue)
                        .map(Objects::toString).orElse(null))
                .metadataMask(Optional.ofNullable(match.getMasked(MatchField.METADATA))
                        .map(Masked::getMask)
                        .map(Objects::toString).orElse(null))
                .build();
    }

    private String getTunnelId(final Match match) {
        if (match.get(MatchField.TUNNEL_ID) != null) {
            return match.get(MatchField.TUNNEL_ID).toString();
        } else if (match.get(MatchField.KILDA_VXLAN_VNI) != null) {
            return match.get(MatchField.KILDA_VXLAN_VNI).toString();
        }
        return null;
    }

    /**
     * Convert list of {@link OFInstruction} to {@link FlowInstructions}.
     * @param instructions list of instructions to be converted.
     * @return result of transformation {@link FlowInstructions}.
     */
    public FlowInstructions toFlowInstructions(final List<OFInstruction> instructions) {
        FlowInstructions.FlowInstructionsBuilder flowInstructions = FlowInstructions.builder();

        for (OFInstruction entry : instructions) {
            if (entry instanceof OFInstructionApplyActions) {
                List<OFAction> actions = ((OFInstructionApplyActions) entry).getActions();
                flowInstructions.applyActions(toFlowApplyActions(actions));
            } else if (entry instanceof OFInstructionMeter) {
                flowInstructions.goToMeter(((OFInstructionMeter) entry).getMeterId());
            } else if (entry instanceof OFInstructionGotoTable) {
                flowInstructions.goToTable(((OFInstructionGotoTable) entry).getTableId().getValue());
            }
            // add handling for other instructions here
        }

        return flowInstructions.build();
    }

    /**
     * Convert {@link OFGroupDescStatsEntry} to {@link GroupEntry}.
     *
     * @param ofGroupDescStatsEntry group description.
     * @return result of transformation {@link GroupEntry}.
     */
    public GroupEntry toFlowGroupEntry(OFGroupDescStatsEntry ofGroupDescStatsEntry) {
        if (ofGroupDescStatsEntry == null) {
            return null;
        }
        return GroupEntry.builder()
                .groupType(ofGroupDescStatsEntry.getGroupType().toString())
                .groupId(ofGroupDescStatsEntry.getGroup().getGroupNumber())
                .buckets(ofGroupDescStatsEntry.getBuckets().stream()
                        .map(this::toGroupBucket)
                        .collect(toList()))
                .build();
    }

    /**
     * Convert {@link OFBucket} to {@link GroupBucket}.
     *
     * @param ofBucket group bucket.
     * @return result of transformation {@link GroupEntry}.
     */
    public GroupBucket toGroupBucket(OFBucket ofBucket) {
        if (ofBucket == null) {
            return null;
        }
        int watchPort = -1;
        if (ofBucket.getWatchPort() != null) {
            watchPort = ofBucket.getWatchPort().getPortNumber();
        }
        return new GroupBucket(watchPort, toFlowApplyActions(ofBucket.getActions()));
    }

    /**
     * Partial convert of {@link OFInstruction} to {@link FlowApplyActions}.
     */
    public FlowApplyActions toFlowApplyActions(List<OFAction> ofApplyActions) {
        FlowApplyActions.FlowApplyActionsBuilder flowActions = FlowApplyActions.builder();

        for (OFAction action : ofApplyActions) {
            if (action instanceof OFActionMeter) {
                fillFlowAction(flowActions, (OFActionMeter) action);
            } else if (action instanceof OFActionPushVlan) {
                fillFlowAction(flowActions, (OFActionPushVlan) action);
            } else if (action instanceof OFActionOutput) {
                fillFlowAction(flowActions, (OFActionOutput) action);
            } else if (action instanceof OFActionSetField) {
                fillFlowAction(flowActions, (OFActionSetField) action);
            } else if (action instanceof OFActionNoviflowPushVxlanTunnel) {
                fillFlowAction(flowActions, (OFActionNoviflowPushVxlanTunnel) action);
            } else if (action instanceof OFActionKildaPushVxlanField) {
                fillFlowAction(flowActions, (OFActionKildaPushVxlanField) action);
            } else if (action instanceof OFActionGroup) {
                fillFlowAction(flowActions, (OFActionGroup) action);
            } else if (action instanceof OFActionNoviflowCopyField) {
                fillFlowAction(flowActions, (OFActionNoviflowCopyField) action);
            } else if (action instanceof OFActionNoviflowSwapField) {
                fillFlowAction(flowActions, (OFActionNoviflowSwapField) action);
            } else if (action instanceof OFActionKildaSwapField) {
                fillFlowAction(flowActions, (OFActionKildaSwapField) action);
            }
            // add handling for other actions here
        }

        return flowActions.build();
    }

    /**
     * Convert list of {@link OFFlowStatsReply} to {@link FlowStatsData}.
     * @param data list of flow stats replies to be converted.
     * @param switchId id of the switch from which these replies were gotten.
     * @return result of transformation {@link FlowStatsData}.
     */
    public FlowStatsData toFlowStatsData(List<OFFlowStatsReply> data, SwitchId switchId) {
        try {
            List<FlowStatsEntry> stats = data.stream()
                    .flatMap(reply -> reply.getEntries().stream())
                    .map(this::toFlowStatsEntry)
                    .filter(Objects::nonNull)
                    .collect(toList());
            return new FlowStatsData(switchId, stats);
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert flow stats data %s on switch %s", data, switchId), e);
            return null;
        }
    }

    /**
     * Convert {@link OFFlowStatsEntry} to {@link FlowStatsEntry}.
     * @param entry flow stats entry to be converted.
     * @return result of transformation {@link FlowStatsEntry} or null if object couldn't be parsed.
     */
    public FlowStatsEntry toFlowStatsEntry(OFFlowStatsEntry entry) {
        try {
            return new FlowStatsEntry(
                    entry.getTableId().getValue(),
                    entry.getPriority(),
                    entry.getCookie().getValue(),
                    entry.getPacketCount().getValue(),
                    entry.getByteCount().getValue(),
                    locateInPort(entry.getMatch()),
                    locateOutPort(entry.getInstructions()));
        } catch (NullPointerException | UnsupportedOperationException | IllegalArgumentException e) {
            log.error(String.format("Could not convert OFFlowStatsEntry object %s", entry), e);
            return null;
        }
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionMeter action) {
        flowActions.meter(String.valueOf(action.getMeterId()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionPushVlan action) {
        flowActions.firstPushVlan(String.valueOf(action.getEthertype().toString()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionOutput action) {
        flowActions.flowOutput(String.valueOf(action.getPort().toString()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionSetField action) {
        OFOxm<?> field = action.getField();
        String value = field.getValue().toString();

        if (MatchField.VLAN_VID.getName().equals(field.getMatchField().getName())) {
            value = String.valueOf(Long.decode(value) & VLAN_MASK);
        }

        flowActions.setFieldAction(new FlowSetFieldAction(field.getMatchField().getName(), value));
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionNoviflowPushVxlanTunnel action) {
        flowActions.pushVxlan(String.valueOf(action.getVni()));
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionKildaPushVxlanField action) {
        flowActions.pushVxlan(String.valueOf(action.getVni()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionGroup action) {
        flowActions.group(String.valueOf(action.getGroup()));
    }

    private void fillFlowAction(
            FlowApplyActionsBuilder flowActions, OFActionNoviflowCopyField action) {
        flowActions.copyFieldAction(FlowCopyFieldAction.builder()
                .bits(String.valueOf(action.getNBits()))
                .srcOffset(String.valueOf(action.getSrcOffset()))
                .dstOffset(String.valueOf(action.getDstOffset()))
                .srcOxm(String.valueOf(action.getOxmSrcHeader()))
                .dstOxm(String.valueOf(action.getOxmDstHeader()))
                .build());
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionNoviflowSwapField action) {
        flowActions.swapFieldAction(FlowSwapFieldAction.builder()
                .bits(String.valueOf(action.getNBits()))
                .srcOffset(String.valueOf(action.getSrcOffset()))
                .dstOffset(String.valueOf(action.getDstOffset()))
                .srcOxm(String.valueOf(action.getOxmSrcHeader()))
                .dstOxm(String.valueOf(action.getOxmDstHeader()))
                .build());
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionKildaSwapField action) {
        flowActions.swapFieldAction(FlowSwapFieldAction.builder()
                .bits(String.valueOf(action.getNBits()))
                .srcOffset(String.valueOf(action.getSrcOffset()))
                .dstOffset(String.valueOf(action.getDstOffset()))
                .srcOxm(String.valueOf(action.getOxmSrcHeader()))
                .dstOxm(String.valueOf(action.getOxmDstHeader()))
                .build());
    }

    private int locateInPort(Match match) {
        if (match != null && match.get(MatchField.IN_PORT) != null) {
            return match.get(MatchField.IN_PORT).getPortNumber();
        }
        return 0;
    }

    private int locateOutPort(List<OFInstruction> instructions) {
        if (instructions == null) {
            return 0;
        }
        Optional<OFInstructionApplyActions> applyActions = instructions.stream()
                                                                   .filter(x -> x instanceof OFInstructionApplyActions)
                                                                   .map(OFInstructionApplyActions.class::cast)
                                                                   .findAny();
        if (!applyActions.isPresent()) {
            return 0;
        }

        Optional<OFActionOutput> outputAction = applyActions.get().getActions()
                .stream()
                    .filter(x -> x instanceof OFActionOutput)
                    .map(OFActionOutput.class::cast)
                    .findAny();
        if (outputAction.isPresent()) {
            return outputAction.get().getPort().getPortNumber();
        }
        return 0;
    }
}
