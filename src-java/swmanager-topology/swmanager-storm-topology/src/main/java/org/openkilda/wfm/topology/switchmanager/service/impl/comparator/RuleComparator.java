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

package org.openkilda.wfm.topology.switchmanager.service.impl.comparator;

import static java.lang.String.format;

import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowApplyActions.FlowApplyActionsBuilder;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowInstructions.FlowInstructionsBuilder;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowMatchField.FlowMatchFieldBuilder;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import org.apache.commons.lang.builder.EqualsBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utils class to compare rules.
 */
public final class RuleComparator {

    private RuleComparator() {
    }

    /**
     * Compares two flow representations.
     *
     * @return true if flows are equal.
     */
    public static boolean compareRules(FlowEntry flowEntry, FlowSpeakerCommandData flowCommand) {
        return new EqualsBuilder()
                .append(flowEntry.getCookie(), flowCommand.getCookie().getValue())
                .append(flowEntry.getTableId(), flowCommand.getTable().getTableId())
                .append(flowEntry.getPriority(), flowCommand.getPriority())
                .append(flowEntry.getMatch(), convertMatch(flowCommand.getMatch()))
                .append(flowEntry.getInstructions(), convertInstructions(flowCommand.getInstructions()))
                .append(convertFlags(flowEntry.getFlags()), flowCommand.getFlags())
                .isEquals();
    }

    private static FlowMatchField convertMatch(Set<FieldMatch> flowCommandMatch) {
        if (flowCommandMatch == null) {
            return null;
        }
        FlowMatchFieldBuilder builder = FlowMatchField.builder();
        flowCommandMatch.forEach(fieldMatch -> processFieldMatch(builder, fieldMatch));
        return builder.build();
    }

    private static void processFieldMatch(FlowMatchFieldBuilder builder, FieldMatch fieldMatch) {
        switch (fieldMatch.getField()) {
            case ETH_TYPE:
                builder.ethType(String.valueOf(fieldMatch.getValue()));
                break;
            case ETH_SRC:
                builder.ethSrc(String.valueOf(fieldMatch.getValue()));
                break;
            case ETH_DST:
                builder.ethDst(String.valueOf(fieldMatch.getValue()));
                break;
            case IN_PORT:
                builder.inPort(String.valueOf(fieldMatch.getValue()));
                break;
            case UDP_SRC:
                builder.udpSrc(String.valueOf(fieldMatch.getValue()));
                break;
            case UDP_DST:
                builder.udpDst(String.valueOf(fieldMatch.getValue()));
                break;
            case IP_PROTO:
                builder.ipProto(String.valueOf(fieldMatch.getValue()));
                break;
            case METADATA:
                builder.metadataMask(String.valueOf(fieldMatch.getMask()));
                builder.metadataValue(String.valueOf(fieldMatch.getValue()));
                break;
            //todo (rule-manager-fl-integration): add other fields
            default:
                throw new IllegalStateException(format("Unknown match field %s", fieldMatch.getField()));
        }
    }

    private static FlowInstructions convertInstructions(Instructions flowCommandInstructions) {
        if (flowCommandInstructions == null) {
            return null;
        }
        FlowInstructionsBuilder builder = FlowInstructions.builder();
        if (flowCommandInstructions.getGoToTable() != null) {
            builder.goToTable((short) flowCommandInstructions.getGoToTable().getTableId());
        }
        if (flowCommandInstructions.getGoToMeter() != null) {
            builder.goToMeter(flowCommandInstructions.getGoToMeter().getValue());
        }
        if (flowCommandInstructions.getApplyActions() != null) {
            builder.applyActions(convertApplyActions(flowCommandInstructions.getApplyActions()));
        }
        return builder.build();
    }

    private static FlowApplyActions convertApplyActions(List<Action> actions) {
        FlowApplyActionsBuilder builder = FlowApplyActions.builder();
        actions.forEach(action -> processAction(builder, action));
        return builder.build();
    }

    private static void processAction(FlowApplyActionsBuilder builder, Action action) {
        switch (action.getType()) {
            case PORT_OUT:
                PortOutAction portOutAction = (PortOutAction) action;
                builder.flowOutput(portOutAction.getPortNumber().toString());
                break;
            case METER:
                MeterAction meterAction = (MeterAction) action;
                builder.meter(meterAction.getMeterId().toString());
                break;
            case GROUP:
                GroupAction groupAction = (GroupAction) action;
                builder.group(groupAction.getGroupId().toString());
                break;
            //todo (rule-manager-fl-integration): add other actions
            default:
                throw new IllegalStateException(format("Unknown action type %s", action.getType()));
        }
    }

    private static Set<OfFlowFlag> convertFlags(String[] flags) {
        if (flags == null) {
            return null;
        }
        return Stream.of(flags).map(OfFlowFlag::valueOf).collect(Collectors.toSet());
    }
}
