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
import org.openkilda.messaging.info.rule.FlowCopyFieldAction;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowCopyField;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowPushVxlanTunnel;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPopVlan;
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
import org.projectfloodlight.openflow.types.OFMetadata;

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
                .tunnelId(Optional.ofNullable(match.get(MatchField.TUNNEL_ID))
                        .map(Objects::toString).orElse(null))
                .metadata(Optional.ofNullable(match.getMasked(MatchField.METADATA))
                        .map(this::formatMatchMaskedMetadata).orElse(null))
                .build();
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
            // do handle other actions types now
        }

        return flowInstructions.build();
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
            } else if (action instanceof OFActionPopVlan) {
                fillFlowAction(flowActions, (OFActionPopVlan) action);
            } else if (action instanceof OFActionOutput) {
                fillFlowAction(flowActions, (OFActionOutput) action);
            } else if (action instanceof OFActionSetField) {
                fillFlowAction(flowActions, (OFActionSetField) action);
            } else if (action instanceof OFActionNoviflowPushVxlanTunnel) {
                fillFlowAction(flowActions, (OFActionNoviflowPushVxlanTunnel) action);
            } else if (action instanceof OFActionGroup) {
                fillFlowAction(flowActions, (OFActionGroup) action);
            } else if (action instanceof OFActionNoviflowCopyField) {
                fillFlowAction(flowActions, (OFActionNoviflowCopyField) action);
            }
            // do handle other actions types now
        }

        return flowActions.build();
    }

    /**
     * Convert {@link OFAction} to {@link FlowCopyFieldAction}.
     * @param action action to be converted.
     * @return result of transformation to {@link FlowCopyFieldAction}.
     */
    public FlowCopyFieldAction toFlowSetCopyFieldAction(OFAction action) {
        OFActionNoviflowCopyField setCopyFieldAction = (OFActionNoviflowCopyField) action;

        return FlowCopyFieldAction.builder()
                .bits(String.valueOf(setCopyFieldAction.getNBits()))
                .srcOffset(String.valueOf(setCopyFieldAction.getSrcOffset()))
                .dstOffset(String.valueOf(setCopyFieldAction.getDstOffset()))
                .srcOxm(String.valueOf(setCopyFieldAction.getOxmSrcHeader()))
                .dstOxm(String.valueOf(setCopyFieldAction.getOxmDstHeader()))
                .build();
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
            return new FlowStatsEntry(entry.getTableId().getValue(),
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
        flowActions.pushVlan(String.valueOf(action.getEthertype().toString()));
        flowActions.encapsulationAction("vlan_push");
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionPopVlan action) {
        flowActions.encapsulationAction("vlan_pop");
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionOutput action) {
        flowActions.flowOutput(String.valueOf(action.getPort().toString()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionSetField action) {
        OFOxm<?> field = action.getField();
        String value = field.getValue().toString();

        if (MatchField.VLAN_VID.getName().equals(field.getMatchField().getName())) {
            value = String.valueOf(Long.decode(value) & VLAN_MASK);
            flowActions.encapsulationAction(String.format("vlan_vid=%s", value));
        }

        flowActions.fieldAction(new FlowSetFieldAction(field.getMatchField().getName(), value));
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionNoviflowPushVxlanTunnel action) {
        flowActions.pushVxlan(String.valueOf(action.getVni()));
    }

    private void fillFlowAction(FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionGroup action) {
        flowActions.group(String.valueOf(action.getGroup()));
    }

    private void fillFlowAction(
            FlowApplyActions.FlowApplyActionsBuilder flowActions, OFActionNoviflowCopyField action) {
        flowActions.copyFieldAction(toFlowSetCopyFieldAction(action));
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

    private String formatMatchMaskedMetadata(Masked<OFMetadata> metadataMatch) {
        OFMetadata value = metadataMatch.getValue();
        OFMetadata mask = metadataMatch.getMask();
        return String.format("0x%016x/0x%016x", value.getValue().getValue(),  mask.getValue().getValue());
    }
}
